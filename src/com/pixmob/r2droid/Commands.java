/*
 * Copyright (C) 2011 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixmob.r2droid;

import static com.pixmob.r2droid.Constants.DEV;
import static com.pixmob.r2droid.Constants.TAG;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;

/**
 * Implementation for supported commands.
 * @author Pixmob
 */
final class Commands {
    private static final long[] VIBRATOR_PATTERN = { 0, 500, 300 };
    
    private Commands() {
    }
    
    /**
     * Make the device ring.
     */
    public static void ring(Context context)
            throws CommandExecutionFailedException, InterruptedException {
        final Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_RINGTONE);
        final MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_RING);
        try {
            player.setDataSource(context, ringtoneUri);
        } catch (IOException e) {
            throw new CommandExecutionFailedException(
                    "Failed to initialize MediaPlayer for " + ringtoneUri, e);
        }
        try {
            player.prepare();
        } catch (IOException e) {
            throw new CommandExecutionFailedException(
                    "Failed to prepare MediaPlayer for " + ringtoneUri, e);
        }
        
        final CountDownLatch barrier = new CountDownLatch(1);
        player.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                barrier.countDown();
            }
        });
        
        try {
            player.start();
            barrier.await();
        } finally {
            barrier.countDown();
            player.release();
        }
    }
    
    public static void vibrate(Context context) throws InterruptedException {
        final Vibrator vibrator = (Vibrator) context
                .getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(VIBRATOR_PATTERN, 1);
        try {
            Thread.sleep(1000 * 10);
        } finally {
            vibrator.cancel();
        }
    }
    
    public static void say(Context context, String text)
            throws CommandExecutionFailedException, InterruptedException {
        final CountDownLatch initBarrier = new CountDownLatch(1);
        if (DEV) {
            Log.d(TAG, "Initializing TTS");
        }
        final TextToSpeech tts = new TextToSpeech(context,
                new OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        initBarrier.countDown();
                    }
                });
        
        try {
            initBarrier.await();
            
            if (DEV) {
                Log.d(TAG, "TTS initialized");
            }
            
            // check if TTS resources are available
            Locale locale = context.getResources().getConfiguration().locale;
            final int languageResult = tts.isLanguageAvailable(locale);
            if (TextToSpeech.LANG_MISSING_DATA == languageResult) {
                throw new CommandExecutionFailedException(
                        "Missing text-to-speech data: "
                                + "you may install TTS package from Android Market");
            }
            if (TextToSpeech.LANG_NOT_SUPPORTED == languageResult) {
                // defaulting to english if the language is not supported
                locale = Locale.ENGLISH;
            }
            if (DEV) {
                Log.d(TAG, "Using " + locale + " for TTS");
            }
            tts.setLanguage(locale);
            
            final HashMap<String, String> ttsParams = new HashMap<String, String>(
                    2);
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String
                    .valueOf(AudioManager.STREAM_RING));
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            
            final CountDownLatch playBarrier = new CountDownLatch(1);
            final OnUtteranceCompletedListener ttsListener = new OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    playBarrier.countDown();
                }
            };
            tts.setOnUtteranceCompletedListener(ttsListener);
            
            if (DEV) {
                Log.d(TAG, "Speak using TTS: " + text);
            }
            tts.speak(text, TextToSpeech.QUEUE_ADD, ttsParams);
            
            playBarrier.await();
            if (DEV) {
                Log.d(TAG, "Speak done");
            }
        } finally {
            tts.shutdown();
        }
    }
}
