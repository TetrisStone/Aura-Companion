#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <cmath>
#include <string>
#include <sstream>
#include <vector>
#include <unistd.h>
#include <sampling.h>
#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch, sampler and performance tracking
 */
constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;

static llama_model                      * g_model = nullptr;
static llama_context                    * g_context = nullptr;
static common_batch                       g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler = nullptr;

static int g_configured_context_size = DEFAULT_CONTEXT_SIZE;
static int g_configured_threads = 6;
static int g_configured_gpu_layers = 0;

// Performance metrics for logging and diagnosis
static double g_model_load_time_ms = 0.0;
static double g_prompt_eval_time_ms = 0.0;
static int    g_prompt_tokens_count = 0;
static double g_gen_eval_time_ms = 0.0;
static int    g_gen_tokens_count = 0;
static int64_t g_last_gen_token_time_us = 0;

static std::string get_backend_name() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(name);
        }
    }
    if (g_configured_gpu_layers > 0 && !backends.empty()) {
        return "GPU (" + join(backends, ",") + ")";
    }
    return "CPU";
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    llama_log_set(aichat_android_log_callback, nullptr);

    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(
        JNIEnv *env,
        jobject,
        jstring jmodel_path,
        jint n_gpu_layers
) {
    const int64_t t_start = ggml_time_us();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;
    g_configured_gpu_layers = n_gpu_layers;

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from %s with n_gpu_layers = %d", __func__, model_path, n_gpu_layers);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!model) {
        LOGe("%s: Failed to load model from %s", __func__, model_path);
        return 1;
    }
    g_model = model;

    const int64_t t_end = ggml_time_us();
    g_model_load_time_ms = double(t_end - t_start) / 1000.0;
    LOGi("%s: Model loaded in %.2f ms. Active backend config: %s",
         __func__, g_model_load_time_ms, get_backend_name().c_str());

    return 0;
}

static llama_context *init_context(llama_model *model, const int n_ctx, const int n_threads) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    const int actual_threads = (n_threads > 0) ? n_threads : 6;
    LOGi("%s: Using %d threads, n_ctx = %d", __func__, actual_threads, n_ctx);

    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = actual_threads;
    ctx_params.n_threads_batch = actual_threads;

    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_init_from_model() returned null", __func__);
    }
    return context;
}

static common_sampler *new_sampler(float temp, float top_p) {
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_p = top_p;
    return common_sampler_init(g_model, sparams);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(
        JNIEnv * /*env*/,
        jobject /*unused*/,
        jint n_ctx,
        jint n_threads,
        jfloat temperature,
        jfloat top_p
) {
    g_configured_context_size = (n_ctx > 0) ? n_ctx : DEFAULT_CONTEXT_SIZE;
    g_configured_threads = (n_threads > 0) ? n_threads : 6;

    auto *context = init_context(g_model, g_configured_context_size, g_configured_threads);
    if (!context) { return 1; }

    g_context = context;
    g_batch = common_batch(context);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = new_sampler(temperature, top_p);
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

constexpr const char *ROLE_SYSTEM    = "system";
constexpr const char *ROLE_USER      = "user";
constexpr const char *ROLE_ASSISTANT = "assistant";

static std::vector<common_chat_msg> chat_msgs;

static int current_position = 0;
static int stop_generation_position = 0;
static int system_prompt_position = 0;
static std::string cached_token_chars = "";
static std::stringstream assistant_ss;

static void reset_long_term_states() {
    llama_memory_clear(llama_get_memory(g_context), true);
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;
    cached_token_chars = "";
    assistant_ss.str("");
    assistant_ss.clear();
}

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars = "";
    assistant_ss.str("");
    assistant_ss.clear();
}

static void shift_context() {
    const int n_keep = system_prompt_position;
    const int n_discard = (current_position - n_keep) / 2;
    LOGi("%s: Shifting context: keeping %d, discarding %d tokens", __func__, n_keep, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, n_keep, n_keep + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, n_keep + n_discard, current_position, -n_discard);
    current_position -= n_discard;
}

static int decode_tokens_in_batches(llama_context *context, common_batch &batch,
                                   const std::vector<llama_token> &tokens,
                                   int start_pos, bool is_eval_needed = false) {
    const int n_tokens = (int) tokens.size();
    for (int i = 0; i < n_tokens; i += BATCH_SIZE) {
        batch.clear();
        const int batch_len = std::min(BATCH_SIZE, n_tokens - i);
        for (int j = 0; j < batch_len; j++) {
            const bool is_last = (is_eval_needed && (i + j == n_tokens - 1));
            batch.add(tokens[i + j], start_pos + i + j, 0, is_last);
        }
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            return 1;
        }
    }
    return 0;
}


static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    if (!g_chat_templates) {
        chat_msgs.push_back(new_msg);
        return content;
    }
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    return formatted;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    reset_long_term_states();
    reset_short_term_states();

    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    std::string formatted_system_prompt(system_prompt);

    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);

    const int max_batch_size = g_configured_context_size - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    system_prompt_position = current_position = (int) system_tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    reset_short_term_states();

    const int64_t t_prompt_start = ggml_time_us();

    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string formatted_user_prompt(user_prompt);

    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);

    const int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = g_configured_context_size - OVERFLOW_HEADROOM;
    if (user_prompt_size > max_batch_size) {
        const int skipped = user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens", __func__, skipped);
    }

    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    const int64_t t_prompt_end = ggml_time_us();
    g_prompt_tokens_count = (int) user_tokens.size();
    g_prompt_eval_time_ms = double(t_prompt_end - t_prompt_start) / 1000.0;

    // Reset generation timing
    g_gen_tokens_count = 0;
    g_gen_eval_time_ms = 0.0;
    g_last_gen_token_time_us = ggml_time_us();

    current_position += (int) user_tokens.size();
    stop_generation_position = current_position + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }
    const auto *bytes = (const unsigned char *) string;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) { return false; }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    if (current_position >= g_configured_context_size - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    if (current_position >= stop_generation_position) {
        return nullptr;
    }

    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    g_batch.clear();
    g_batch.add(new_token_id, current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }

    current_position++;
    g_gen_tokens_count++;
    const int64_t now_us = ggml_time_us();
    g_gen_eval_time_ms = double(now_us - g_last_gen_token_time_us) / 1000.0;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        result = env->NewStringUTF("");
    }
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeGetDiagnostics(JNIEnv *env, jobject /*unused*/) {
    const double pp_speed = (g_prompt_eval_time_ms > 0)
        ? (double(g_prompt_tokens_count) / (g_prompt_eval_time_ms / 1000.0)) : 0.0;
    const double tg_speed = (g_gen_eval_time_ms > 0)
        ? (double(g_gen_tokens_count) / (g_gen_eval_time_ms / 1000.0)) : 0.0;

    std::ostringstream ss;
    ss << "Backend: " << get_backend_name() << "\n";
    ss << "Ladezeit: " << std::fixed << std::setprecision(1) << g_model_load_time_ms << " ms\n";
    ss << "Prompt-Verarbeitung: " << std::fixed << std::setprecision(1) << pp_speed << " Tokens/s ("
       << g_prompt_tokens_count << " Tokens in " << g_prompt_eval_time_ms << " ms)\n";
    ss << "Generierung: " << std::fixed << std::setprecision(1) << tg_speed << " Tokens/s ("
       << g_gen_tokens_count << " Tokens in " << g_gen_eval_time_ms << " ms)";

    return env->NewStringUTF(ss.str().c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    reset_long_term_states();
    reset_short_term_states();

    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(
        JNIEnv *env,
        jobject /*unused*/,
        jint pp,
        jint tg,
        jint pl,
        jint nr
) {
    auto *context = init_context(g_model, pp, g_configured_threads);
    if (!context) {
        return env->NewStringUTF("Failed to init context for bench");
    }
    llama_free(context);
    return env->NewStringUTF("Bench completed");
}
