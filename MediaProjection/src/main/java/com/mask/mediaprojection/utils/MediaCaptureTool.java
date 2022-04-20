package com.mask.mediaprojection.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;


import com.mask.mediaprojection.interfaces.MediaRecorderCallback;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaCaptureTool {
    private static final String TAG = "MediaCaptureTool-yufeng";

    public MediaCaptureTool() {
    }


    //录制屏幕 加声音 同步方式
    public static class AudioRecorderThread extends Thread {
        private AudioRecord mAudiorecord;//录音类

        private MediaMuxer mMediaMuxer;//通过这个将视频流写入本地文件，如果直播的话 不需要这个

        // 音频源：音频输入-麦克风  我使用其他格式 就会报错
        private final static int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
        // 采样率
        // 44100是目前的标准，但是某些设备仍然支持22050，16000，11025
        // 采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
        private final static int AUDIO_SAMPLE_RATE = 44100;
        // 音频通道 默认的 可以是单声道 立体声道
        private final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_DEFAULT;
        // 音频格式：PCM编码   返回音频数据的格式
        private final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        //记录期间写入音频数据的缓冲区的总大小(以字节为单位)。
        private int audioBufferSize = 0;
        //缓冲数组 ，用来读取audioRecord的音频数据
        private byte[] byteBuffer;
        private static final int Mbps = (int)Math.pow(10,6);//10^6

        private int audioIndex;//通过MediaMuxer 向本地文件写入数据时候，这个标志是用来确定信道的

        private int videoIndex;//上同

        private MediaCodec mAudioMediaCodec;//音频编码器

        private MediaCodec mVideoMediaCodec;//视频编码器

        private MediaFormat audioFormat;//音频编码器 输出数据的格式
        private MediaFormat videoFormat;//视频编码器 输出数据的格式

        private final MediaProjection mediaProjection;//通过这个类 生成虚拟屏幕
        private Surface surface;//视频编码器 生成的surface ，用于充当 视频编码器的输入源
        private VirtualDisplay virtualDisplay; //虚拟屏幕
        //这个是每次在编码器 取数据的时候，这个info 携带取出数据的信息，例如 时间，大小 类型之类的  关键帧 可以通过这里的flags辨别
        private final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();

        private final MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();

        private volatile boolean isRun = true;//用于控制 是否录制，这个无关紧要


        private int mWidth;//录制视频的宽
        private int mHeight;//录制视频的高
        private int mBitRate;//比特率 bits per second  这个经过我测试 并不是 一定能达到这个值
        private int mDpi;//视频的DPI
        private String mDstPath;//录制视频文件存放地点
        private int FRAME_RATE = 60;//视频帧数 一秒多少张画面 并不一定能达到这个值
        private int orientation = 0;
        private MediaRecorderCallback callback;

        public AudioRecorderThread(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath) {
            this.mediaProjection = mediaProjection;
            this.mWidth = width;
            this.mHeight = height;
            mBitRate = bitrate;
            mDpi = dpi;
            mDstPath = dstPath;
        }

        public AudioRecorderThread(MediaRecorderCallback callback,RecorderParam recorderParam, MediaProjection mediaProjection) {
            this.mediaProjection = mediaProjection;
            this.callback = callback;
            initParam(recorderParam);
        }

        private void initParam(RecorderParam recorderParam){
            Log.d(TAG, "initParam: recorderParam:"+recorderParam.toString());
            int width = 800;
            int height = 400;
            if (recorderParam.getResolution().equalsIgnoreCase(RecorderParam.RESOLUTION.LOW)){
                width = 1920;
                height = 912;
            }
            this.mWidth = width;
            this.mHeight = height;

            mBitRate = recorderParam.getQuality()*Mbps;

            if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_15) {
                FRAME_RATE = 15;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_24) {
                FRAME_RATE = 24;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_30) {
                FRAME_RATE = 30;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_48) {
                FRAME_RATE = 48;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_60) {
                FRAME_RATE = 60;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_90) {
                FRAME_RATE = 90;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_120) {
                FRAME_RATE = 120;
            }else if (recorderParam.getFrameRate() == RecorderParam.FRAME_RATE.FPS_240) {
                FRAME_RATE = 240;
            }


            if (recorderParam.getOrientation() == RecorderParam.ORIENTATION.DEGREE_90) {
                orientation = 90;
            } else if (recorderParam.getOrientation() == RecorderParam.ORIENTATION.DEGREE_180){
                orientation = 180;
            }else if (recorderParam.getOrientation() == RecorderParam.ORIENTATION.DEGREE_270){
                orientation = 270;
            }

            mDpi = 560;
            mDstPath = "yufeng.mp4";
        }

        @Override
        public void run() {
            super.run();
            Log.d(TAG, "run: ");
            try {
                //实例化 AudioRecord
                initAudioRecord();

                //实例化 写入文件的类
                initMediaMuxer(mDstPath);

                //实例化 音频编码器
                initAudioMediacode();


                //实例化 视频编码器
                initVideoMedicodec();

                //开始
                mAudioMediaCodec.start();

                mVideoMediaCodec.start();

                int timeoutUs = -1;//这个主要是为了 第一次进入while循环 视频编码器 能阻塞到 有视频数据输出 才运行

                while (isRun) {

                    //获取 输出缓冲区的索引 通过索引 可以去到缓冲区，缓冲区里面存着 编码后的视频数据 。 timeoutUs为负数的话，会一直阻塞到有缓冲区索引，0的话 立刻返回
                    int videoOutputID = mVideoMediaCodec.dequeueOutputBuffer(videoInfo, timeoutUs);
                    //Log.d(TAG, "video flags " + videoInfo.flags);
                    timeoutUs = 0;//第二次 视频编码器 就不需要 阻塞了  0 立刻返回
                    //索引大于等于0 就代表有数据了
                    if (videoOutputID >= 0) {
                        //Log.d(TAG, "yufeng VIDEO 输出"+ videoOutputID+videoInfo.presentationTimeUs);
                        //flags是2的时候 代表输出的数据 是配置信息，不是媒体信息
                        if (videoInfo.flags != 2) {
                            //得到缓冲区
                            //这里就可以取出数据 进行网络传输
                            ByteBuffer outBuffer = mVideoMediaCodec.getOutputBuffer(videoOutputID);
                            outBuffer.flip();//准备读取
                            //写入文件中  注意 videoIndex
                            //Log.d(TAG, "videoIndex " + videoIndex);
                            mMediaMuxer.writeSampleData(videoIndex, outBuffer, videoInfo);
                        }
                        //释放缓冲区，毕竟缓冲区一共就两个 一个输入 一个输出，用完是要还回去的
                        mVideoMediaCodec.releaseOutputBuffer(videoOutputID, false);
                    } else if (videoOutputID == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//输出格式有变化
                        Log.d(TAG, "yufeng video Format 改变");
                        videoFormat = mVideoMediaCodec.getOutputFormat();//得到新的输出格式
                        videoIndex = mMediaMuxer.addTrack(videoFormat);//重新确定信道
                    }
                    //得到 输入缓冲区的索引
                    int audioInputID = mAudioMediaCodec.dequeueInputBuffer(0);
                    //也是大于等于0 代表 可以输入数据啦
                    if (audioInputID >= 0) {
                        //Log.d(TAG, "yufeng audio 输入" + audioInputID);
                        ByteBuffer audioInputBuffer = mAudioMediaCodec.getInputBuffer(audioInputID);
                        audioInputBuffer.clear();
                        //从 audiorecord 里面 读取原始的音频数据
                        int read = mAudiorecord.read(byteBuffer, 0, audioBufferSize);
                        if (read < audioBufferSize) {
                            System.out.println("yufeng 读取的数据" + read);
                        }
                        //上面read可能小于audioBufferSize  要注意
                        audioInputBuffer.put(byteBuffer, 0, read);
                        //入列  注意下面的时间，这个是确定这段数据 时间的 ，视频音频 都是一段段的数据，每个数据都有时间 ，这样播放器才知道 先播放那个数据
                        // 串联起来 就是连续的了
                        mAudioMediaCodec.queueInputBuffer(audioInputID, 0, read, System.nanoTime() / 1000L, 0);
                    }
                    //音频输出
                    int audioOutputID = mAudioMediaCodec.dequeueOutputBuffer(audioInfo, 0);
                    //Log.d(TAG, "audio flags " + audioInfo.flags);
                    if (audioOutputID >= 0) {
                        //Log.d(TAG, "yufeng audio 输出"+audioOutputID+audioInfo.presentationTimeUs);
                        audioInfo.presentationTimeUs = videoInfo.presentationTimeUs;//保持 视频和音频的统一，防止 时间画面声音 不同步
                        if (audioInfo.flags != 2) {
                            //这里就可以取出数据 进行网络传输
                            ByteBuffer audioOutBuffer = mAudioMediaCodec.getOutputBuffer(audioOutputID);
                            audioOutBuffer.limit(audioInfo.offset + audioInfo.size);//这是另一种 和上面的 flip 没区别
                            audioOutBuffer.position(audioInfo.offset);
                            //Log.d(TAG, "audioIndex " + audioIndex);
                            mMediaMuxer.writeSampleData(audioIndex, audioOutBuffer, audioInfo);//写入
                        }
                        //释放缓冲区
                        mAudioMediaCodec.releaseOutputBuffer(audioOutputID, false);
                    } else if (audioOutputID == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "yufeng audio Format 改变");
                        audioFormat = mAudioMediaCodec.getOutputFormat();
                        audioIndex = mMediaMuxer.addTrack(audioFormat);
                        //注意 这里  只在start  视频哪里没有这个，这个方法只能调用一次
                        mMediaMuxer.start();
                    }
                }
                //释放资源
                stopRecorder();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //实例化 AUDIO 的编码器
        void initAudioMediacode() throws IOException {
            audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
            //描述要使用的AAC配置文件的键(仅适用于AAC音频格式)。
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize << 1);//最大输入

            //这里注意  如果 你不确定 你要生成的编码器类型，就通过下面的 通过类型生成编码器
            mAudioMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            //配置
            mAudioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }


        //实例化 VIDEO 的编码器
        void initVideoMedicodec() throws IOException {
            //这里的width height 就是录制视频的分辨率，可以更改  如果这里的分辨率小于 虚拟屏幕的分辨率 ，你会发现 视频只录制了 屏幕部分内容
            videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);//比特率 bit单位
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);//FPS
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);//关键帧  是完整的一张图片，其他的都是部分图片
            //通过类型创建编码器  同理 创建解码器也是一样
            mVideoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            //配置
            mVideoMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //让视频编码器 生成一个弱引用的surface， 这个surface不会保证视频编码器 不被回收，这样 编码视频的时候 就不需要 传输数据进去了
            surface = mVideoMediaCodec.createInputSurface();
            //创建虚拟屏幕，让虚拟屏幕内容 渲染在上面的surface上面 ，这样 才能 不用传输数据进去
            virtualDisplay = mediaProjection.createVirtualDisplay("video", mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
        }

        //录音的类，用于给音频编码器 提供原始数据
        @TargetApi(29)
        void initAudioRecord() {
            //得到 音频录制时候 最小的缓冲区大小
            audioBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE/2, AUDIO_CHANNEL, AUDIO_ENCODING);
            byteBuffer = new byte[audioBufferSize];

            //通过builder方式创建
            AudioPlaybackCaptureConfiguration config =
                    new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();

            mAudiorecord = new AudioRecord.Builder()
                    //.setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(audioBufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();

            //开始录制，这里可以检查一下状态，但只要代码无误，检查是无需的 state
            mAudiorecord.startRecording();
        }


        //如果 要录制mp4文件的话，需要调用这个方法 创建 MediaMuxer
        private void initMediaMuxer(String fileName) throws Exception {
            //注意格式  创建录制的文件
            String filePath = filePath(fileName);
            Log.d(TAG, "initMediaMuxer: filePath "+filePath);
            //实例化 MediaMuxer 编码器取出的数据，通过它写入文件中
            mMediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //设置录屏方向
            mMediaMuxer.setOrientationHint(orientation);
        }

        //释放资源
        public void stopRecorder() {
            if (mVideoMediaCodec != null) {
                mVideoMediaCodec.stop();
                mVideoMediaCodec.release();
                mVideoMediaCodec = null;
            }
            if (mAudioMediaCodec != null) {
                mAudioMediaCodec.stop();
                mAudioMediaCodec.release();
                mAudioMediaCodec = null;
            }
            if (mAudiorecord != null) {
                mAudiorecord.stop();
                mAudiorecord.release();
                mAudiorecord = null;
            }

            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;

            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    /**
     * @param fileName 文件名字
     * @return 文件的地址，默认在sdcard文件夹下
     */
    public static String filePath(String fileName) {
        String state = Environment.getExternalStorageState();
        String filePath;
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName;
        } else {
            filePath = Environment.getDataDirectory().getAbsolutePath() + "/" + fileName;
        }
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdir();
        }
        return filePath;
    }


}
