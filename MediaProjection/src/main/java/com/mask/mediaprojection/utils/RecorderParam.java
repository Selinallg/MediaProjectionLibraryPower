package com.mask.mediaprojection.utils;

import androidx.annotation.IntDef;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class RecorderParam {

    @IntDef(value = {
            SOUND_SOURCE.SYSTEM,
            SOUND_SOURCE.MIC,
            SOUND_SOURCE.MUTE,
            SOUND_SOURCE.SYSTEM_MIC,
    })
    @Retention(RetentionPolicy.CLASS)
    public @interface SOUND_SOURCE {
        int SYSTEM = 0;
        int MIC = 1;
        int MUTE = 2;
        int SYSTEM_MIC =3;
    }

    @StringDef(value = {
            RESOLUTION.LOW,
            RESOLUTION.MID,
            RESOLUTION.HIGH,
    })
    @Retention(RetentionPolicy.CLASS)
    public @interface RESOLUTION {
        String LOW = "800*480";
        String MID = "1280*720";
        String HIGH = "2400*1080";
    }

    @IntDef(value = {
            QUALITY.MBPS_16,
            QUALITY.MBPS_24,
            QUALITY.MBPS_32,
            QUALITY.MBPS_50,
            QUALITY.MBPS_100,
    })
    @Retention(RetentionPolicy.CLASS)
    public @interface QUALITY {
        int MBPS_16 = 16;
        int MBPS_24 = 24;
        int MBPS_32 = 32;
        int MBPS_50 = 50;
        int MBPS_100 = 100;
    }

    @IntDef(value = {
            ORIENTATION.DEGREE_0,
            ORIENTATION.DEGREE_90,
            ORIENTATION.DEGREE_180,
            ORIENTATION.DEGREE_270,
    })
    @Retention(RetentionPolicy.CLASS)
    public @interface ORIENTATION {
        int DEGREE_0 = 0;
        int DEGREE_90 = 90;
        int DEGREE_180 = 180;
        int DEGREE_270 = 270;
    }

    @IntDef(value = {
            FRAME_RATE.FPS_15,
            FRAME_RATE.FPS_24,
            FRAME_RATE.FPS_30,
            FRAME_RATE.FPS_48,
            FRAME_RATE.FPS_60,
            FRAME_RATE.FPS_90,
            FRAME_RATE.FPS_120,
            FRAME_RATE.FPS_240,
    })
    @Retention(RetentionPolicy.CLASS)
    public @interface FRAME_RATE {
        int FPS_15 = 15;
        int FPS_24 = 24;
        int FPS_30 = 30;
        int FPS_48 = 48;
        int FPS_60 = 60;
        int FPS_90 = 90;
        int FPS_120 = 120;
        int FPS_240 = 240;
    }


    private String resolution;//800*480
    private int quality;//16Mbps
    private int orientation;//Portrait,Landscape
    private int soundSource;//System,Mic,Mute
    private int frameRate;//24fps

    public @RESOLUTION String getResolution() {
        return resolution;
    }

    public void setResolution(@RESOLUTION String resolution) {
        this.resolution = resolution;
    }

    public @QUALITY int getQuality() {
        return quality;
    }

    public void setQuality(@QUALITY int quality) {
        this.quality = quality;
    }

    public @ORIENTATION int getOrientation() {
        return orientation;
    }

    public void setOrientation(@ORIENTATION int orientation) {
        this.orientation = orientation;
    }

    public @SOUND_SOURCE int getSoundSource() {
        return soundSource;
    }

    public void setSoundSource(@SOUND_SOURCE int soundSource) {
        this.soundSource = soundSource;
    }

    public @FRAME_RATE int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(@FRAME_RATE int frameRate) {
        this.frameRate = frameRate;
    }

    @Override
    public String toString() {
        return "RecorderParam{" +
                "resolution=" + resolution +
                ", quality=" + quality +
                ", orientation=" + orientation +
                ", soundSource=" + soundSource +
                ", frameRate=" + frameRate +
                '}';
    }
}
