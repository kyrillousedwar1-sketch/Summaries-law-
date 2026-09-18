package com.family.lawofflineai

import android.content.Context
import android.media.*
import android.net.Uri
import com.k2fsa.sherpa.onnx.*
import java.nio.ByteOrder
import kotlin.math.*

class OfflineTranscriber(private val context: Context) {
    private fun recognizer(): OfflineRecognizer {
        val d = "model"
        val whisper = OfflineWhisperModelConfig(
            encoder="$d/whisper-encoder.onnx", decoder="$d/whisper-decoder.onnx",
            language="ar", task="transcribe"
        )
        return OfflineRecognizer(context.assets, OfflineRecognizerConfig(
            featConfig=FeatureConfig(sampleRate=16000, featureDim=80),
            modelConfig=OfflineModelConfig(tokens="$d/tokens.txt", whisper=whisper,
                numThreads=max(2, Runtime.getRuntime().availableProcessors().coerceAtMost(4)),
                provider="cpu", modelType="whisper"),
            decodingMethod="greedy_search"
        ))
    }

    fun transcribe(uri: Uri): String {
        val r=recognizer(); val ex=MediaExtractor(); ex.setDataSource(context,uri,null)
        var track=-1
        for(i in 0 until ex.trackCount){ val f=ex.getTrackFormat(i); if((f.getString(MediaFormat.KEY_MIME)?:"").startsWith("audio/")){track=i;break}}
        require(track>=0){"لم يتم العثور على صوت"}
        ex.selectTrack(track); val fmt=ex.getTrackFormat(track); val mime=fmt.getString(MediaFormat.KEY_MIME)!!
        val rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); val ch=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec=MediaCodec.createDecoderByType(mime); codec.configure(fmt,null,null,0); codec.start()
        val info=MediaCodec.BufferInfo(); var inputDone=false; var done=false; val out=StringBuilder()
        val chunk=FloatArray(16000*20); var n=0
        fun flush(){ if(n==0)return; val s=r.createStream(); s.acceptWaveform(chunk.copyOf(n),16000); r.decode(s)
            val t=r.getResult(s).text.trim(); if(t.isNotEmpty()){if(out.isNotEmpty())out.append("\n");out.append(t)};s.release();n=0 }
        while(!done){
            if(!inputDone){val i=codec.dequeueInputBuffer(10000);if(i>=0){val b=codec.getInputBuffer(i)!!;val sz=ex.readSampleData(b,0)
                if(sz<0){codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                else{codec.queueInputBuffer(i,0,sz,ex.sampleTime,0);ex.advance()}}}
            val i=codec.dequeueOutputBuffer(info,10000)
            if(i>=0){codec.getOutputBuffer(i)?.let{buf->if(info.size>0){buf.position(info.offset);buf.limit(info.offset+info.size)
                val pcm=ShortArray(info.size/2);buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                val mono=FloatArray(pcm.size/ch);for(k in mono.indices){var sum=0;for(c in 0 until ch)sum+=pcm[k*ch+c].toInt();mono[k]=sum.toFloat()/ch/32768f}
                for(v in resample(mono,rate,16000)){chunk[n++]=v;if(n==chunk.size)flush()}}}
                codec.releaseOutputBuffer(i,false);if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)done=true}
        }
        flush();codec.stop();codec.release();ex.release();r.release();return out.toString()
    }
    private fun resample(a:FloatArray,from:Int,to:Int):FloatArray{if(from==to)return a;val m=(a.size.toLong()*to/from).toInt();val o=FloatArray(m);val step=from.toDouble()/to
        for(i in o.indices){val p=i*step;val j=p.toInt().coerceIn(0,a.lastIndex);val k=min(j+1,a.lastIndex);val f=(p-j).toFloat();o[i]=a[j]*(1-f)+a[k]*f};return o}
}
