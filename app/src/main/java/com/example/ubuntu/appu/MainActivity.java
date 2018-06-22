package com.example.ubuntu.appu;

import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    public int activeTimes = 0;
    public boolean isDetectionOn = false;
    private RecordingThread recordingThread;
    private static TextToSpeech mtts;
    private static String projectId = "appu-f5f52";
    private static String sessionId = UUID.randomUUID().toString();
    private static String languageCode = "en-US";
    private static final String CLIENT_ACCESS_TOKEN = "81463c0290a44b16b745f8d5f668d954";

    private TextView speechText;
    private Button recordButton;
    private static SpeechRecognizer mSpeechRecognizer = null;
    private static Intent mSpeechRecognizerIntent = null;

    private OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    public static final String BASE_URL = "https://api.dialogflow.com/v1/query?v=20170712";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
        speechText = (TextView) findViewById(R.id.speechText);
        initHotword();

        mtts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    mtts.setLanguage(Locale.getDefault());
                }
            }
        });

        this.mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        this.mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());


        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {

            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {
                showToast("Endofspeech");
            }

            @Override
            public void onError(int i) {
                showToast("on error speech");
            }

            @Override
            public void onResults(Bundle bundle) {
                //getting all the matches
                ArrayList<String> matches = bundle
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                //displaying the first match
                if (matches != null) {
                    String voiceTextToSpeech = matches.get(0);
                    speechText.setText(voiceTextToSpeech);
                    toggleHotwordListener(true);
                    queryDialogFlow(voiceTextToSpeech);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });
    }

    public void speak(String voiceTextToSpeech) {
        mtts.speak(voiceTextToSpeech,TextToSpeech.QUEUE_FLUSH,null,null);
    }

    public void showToast(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void initHotword() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            AppResCopy.copyResFromAssetsToSD(this);

            recordingThread = new RecordingThread(new VoiceHandler(), new AudioDataSaver());
        }
    }

    public void recordSpeech(View view) {
        if (recordingThread !=null && isDetectionOn) {
            toggleHotwordListener(false);
            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
            speechText.setText("");
            speechText.setHint("Listening...");
        } else {
            mSpeechRecognizer.stopListening();
            speechText.setHint("You will see input here");
            toggleHotwordListener(true);
        }
    }

    public class VoiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MsgEnum message = MsgEnum.getMsgEnum(msg.what);
            switch(message) {
                case MSG_ACTIVE:
                    activeTimes++;
                    // Toast.makeText(Demo.this, "Active "+activeTimes, Toast.LENGTH_SHORT).show();
                    showToast("Active "+activeTimes);
                    recordButton = (Button) findViewById(R.id.recordButton);
                    recordButton.performClick();
                    break;
                case MSG_INFO:
                    showToast("Info "+activeTimes);
                    break;
                case MSG_VAD_SPEECH:
                    showToast("Vad speech "+activeTimes);
                    break;
                case MSG_VAD_END:
                    break;
                case MSG_VAD_NOSPEECH:
                    showToast("vad nospeech "+activeTimes);
                    break;
                case MSG_ERROR:
                    showToast("Err "+activeTimes);
                    break;
                case MSG_VOLUME_NOTIFY:
                    break;
                case MSG_WAV_DATAINFO:
                    break;
                case MSG_RECORD_START:
                    break;
                case MSG_RECORD_STOP:
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finish();
            }
        }
    }

    private void toggleHotwordListener(boolean listenHotword) {
        if(recordingThread !=null && !isDetectionOn && listenHotword) {
            recordingThread.startRecording();
            isDetectionOn = true;
            showToast("hotword listener started");
        } else {
            recordingThread.stopRecording();
            isDetectionOn = false;
            showToast("hotword listener stopped");
        }
    }

    private void queryDialogFlow(String query) {
        JSONObject queryJsonObject = null;
        String queryUrl = BASE_URL;
        try {
            queryJsonObject = new JSONObject();
            queryJsonObject.put("lang", "en");
            queryJsonObject.put("sessionId", sessionId);
            queryJsonObject.put("timezone", "Asia/Calcutta");
            queryJsonObject.put("query", query);
            RequestBody body = RequestBody.create(JSON, queryJsonObject.toString());
            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer "+CLIENT_ACCESS_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .url(queryUrl)
                    .post(body)
                    .build();
            new MyAsyncTask(new ResponseCallback() {
                @Override
                public void onSuccess(String jsonResponseData) {
                    JSONObject responseJsonobject = null;
                    try {
                        responseJsonobject = new JSONObject(jsonResponseData);
                        String speechResponse = responseJsonobject.getJSONObject("result").getJSONObject("fulfillment").getString("speech");
                        showToast(speechResponse);
                        speak(speechResponse);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError() {

                }
            }).execute(request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    interface ResponseCallback {
        void onSuccess(String data);
        void onError();
    }

    class MyAsyncTask extends AsyncTask<Request, Void, Response> {

        ResponseCallback responseCallback;

        MyAsyncTask(ResponseCallback responseCallback) {
            this.responseCallback = responseCallback;
        }

        @Override
        protected Response doInBackground(Request... requests) {
            Response response = null;
            try {
                response = client.newCall(requests[0]).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(Response response) {
            super.onPostExecute(response);
            try {
                if (this.responseCallback !=null ) {
                    if (response.isSuccessful()) {
                        this.responseCallback.onSuccess(response.body().string());
                    } else {
                        this.responseCallback.onError();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStart() {
        toggleHotwordListener(true);
        super.onStart();
    }

    @Override
    public void onDestroy() {
        toggleHotwordListener(false);
        mtts.stop();
        mtts.shutdown();
        super.onDestroy();
    }
}