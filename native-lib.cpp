#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include "llama.h"

static std::string jstr(JNIEnv* env, jstring s) {
    const char* p = env->GetStringUTFChars(s, nullptr);
    std::string out(p ? p : "");
    env->ReleaseStringUTFChars(s, p);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_family_lawofflineai_NativeLlama_generate(JNIEnv* env, jobject,
                                                   jstring modelPath, jstring prompt,
                                                   jint maxTokens) {
    std::string path=jstr(env,modelPath), userPrompt=jstr(env,prompt);
    llama_backend_init();
    llama_model_params mp=llama_model_default_params();
    llama_model* model=llama_model_load_from_file(path.c_str(), mp);
    if(!model) return env->NewStringUTF("تعذر تحميل موديل GGUF");

    llama_context_params cp=llama_context_default_params();
    cp.n_ctx=4096;
    cp.n_batch=512;
    llama_context* ctx=llama_init_from_model(model,cp);
    if(!ctx){llama_model_free(model);return env->NewStringUTF("تعذر إنشاء سياق LLM");}

    const llama_vocab* vocab=llama_model_get_vocab(model);
    std::string full = "You are an Arabic law study assistant. Answer in Arabic. Do not invent facts.\n\nUser:\n"+userPrompt+"\n\nAssistant:\n";
    int n=llama_tokenize(vocab, full.c_str(), (int)full.size(), nullptr, 0, true, true);
    if(n<0)n=-n;
    std::vector<llama_token> toks(n);
    if(llama_tokenize(vocab,full.c_str(),(int)full.size(),toks.data(),n,true,true)<0){
        llama_free(ctx); llama_model_free(model); return env->NewStringUTF("فشل ترميز الطلب");
    }
    llama_batch batch=llama_batch_init(n,0,1);
    for(int i=0;i<n;i++){batch.token[i]=toks[i];batch.pos[i]=i;batch.n_seq_id[i]=1;batch.seq_id[i][0]=0;batch.logits[i]=(i==n-1);}
    batch.n_tokens=n;
    if(llama_decode(ctx,batch)!=0){llama_batch_free(batch);llama_free(ctx);llama_model_free(model);return env->NewStringUTF("فشل بدء الاستدلال");}

    auto* smp=llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smp,llama_sampler_init_greedy());
    std::string result;
    llama_token last=0;
    for(int i=0;i<maxTokens;i++){
        last=llama_sampler_sample(smp,ctx,-1);
        if(last==llama_vocab_eog(vocab))break;
        char buf[8192]; int len=llama_token_to_piece(vocab,last,buf,sizeof(buf),0,true);
        if(len>0) result.append(buf,len);
        llama_batch one=llama_batch_get_one(&last);
        if(llama_decode(ctx,one)!=0)break;
    }
    llama_sampler_free(smp);
    llama_batch_free(batch);
    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    return env->NewStringUTF(result.c_str());
}
