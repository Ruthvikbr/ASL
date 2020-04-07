package com.signlaguage.asl;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;

import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.size.Size;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    CameraView camera;
    FirebaseModelInterpreter interpreter;
    FirebaseModelInputOutputOptions inputOutputOptions;
    TextView resultTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        camera = findViewById(R.id.camera);
        camera.setLifecycleOwner(this);
        resultTextView = findViewById(R.id.resultTextView);
        Button start = findViewById(R.id.startCamera);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        Button stop = findViewById(R.id.stopCamera);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //stopCamera();
            }
        });
    }
    private void takePicture(){
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.raw.test1);
        loadModel(bm);
//        camera.addCameraListener(new CameraListener() {
//            @Override
//            public void onPictureTaken(@NonNull PictureResult result) {
//                byte[] data = result.getData();
//                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
//                Toast.makeText(MainActivity.this," "+bmp.getWidth()+" "+bmp.getHeight(),Toast.LENGTH_SHORT).show();
//                loadModel(bmp);
//            }
//        });
//        camera.takePicture();
    }
    private void startCamera(){
        camera.setFrameProcessingPoolSize(1);
        camera.setFrameProcessingFormat(ImageFormat.YUV_420_888);
        camera.addFrameProcessor(new FrameProcessor() {
            @Override
            public void process(@NonNull Frame frame) {
                long time = frame.getTime();
                Size size = frame.getSize();
                int format = frame.getFormat();
                int userRotation = frame.getRotationToUser();
                int viewRotation = frame.getRotationToView();
                if (frame.getDataClass() == byte[].class) {
                    byte[] data = frame.getData();
                    Log.v("Frame",""+frame.getData());

                    // Convert to Bitmap
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if(bmp==null){
                        Log.v("Null","Null");
                    }else{
                        loadModel(bmp);
                    }

                } else if (frame.getDataClass() == Image.class) {
                    Image data = frame.getData();
                    // Process android.media.Image...
                }
            }
        });
    }

    private void stopCamera(){
        camera.removeFrameProcessor(new FrameProcessor() {
            @Override
            public void process(@NonNull Frame frame) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera.open();
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        camera.destroy();
    }

    private void loadModel(Bitmap bitmap) {


//        ImageView img = new ImageView(this);
//        img.setImageBitmap(bitmap);
//        ((LinearLayout)findViewById(R.id.ll)).addView(img, 0, new LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//        ));
        FirebaseCustomLocalModel localModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("upgraded.tflite")
                .build();


        try {
            FirebaseModelInterpreterOptions options =
                    new FirebaseModelInterpreterOptions.Builder(localModel).build();
            interpreter = FirebaseModelInterpreter.getInstance(options);
        } catch (FirebaseMLException e) {
            Log.v("Firebase Exception", "" + e.getMessage());
        }
        try {
            inputOutputOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 64, 64, 3})
                            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 29})
                            .build();
        } catch (FirebaseMLException e) {
            e.printStackTrace();
        }
        int batchNum = 0;
        float[][][][] input = new float[1][64][64][3];
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int pixel = bitmap.getPixel(x, y);

                input[batchNum][x][y][0] = (Color.red(pixel) - 127) / 128.0f;
                input[batchNum][x][y][1] = (Color.green(pixel) - 127) / 128.0f;
                input[batchNum][x][y][2] = (Color.blue(pixel) - 127) / 128.0f;
            }
        }
        inputImage(input);

    }

    private void inputImage(float[][][][] input) {
        FirebaseModelInputs inputs = null;
        try {
            inputs = new FirebaseModelInputs.Builder()
                    .add(input)  // add() as many input arrays as your model requires
                    .build();
            Task<FirebaseModelOutputs> task = interpreter.run(inputs, inputOutputOptions);
            task.addOnSuccessListener(new OnSuccessListener<FirebaseModelOutputs>() {
                @Override
                public void onSuccess(FirebaseModelOutputs firebaseModelOutputs) {
                    float[][] output = firebaseModelOutputs.getOutput(0);
                    float[] probabilities = output[0];
                    Log.v("probable", " " + Arrays.toString(probabilities));
                    int i = getIndexOfLargest(probabilities);

                    String[] labels = {"A", "B", "C", "D", "DEL", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "NOTHING", "O", "P", "Q", "R", "S", "SPACE"
                            , "T", "U", "V", "W", "X", "Y", "Z"};
                    resultTextView.setText(labels[i]);
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(
                                new InputStreamReader(getAssets().open("labels.txt")));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    for (float probability : probabilities) {
                        String label = null;
                        try {
                            label = reader.readLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Log.v("outputLabel", String.format("%s: %1.4f", label, probability));
                    }
                }
            });
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                         Log.v("Model output failed",""+e.getMessage());
                }
            });
        }catch(FirebaseMLException e){
                e.printStackTrace();
            }
        }


            public int getIndexOfLargest ( float[] array )
            {
                if (array == null || array.length == 0) return -1; // null or empty

                int largest = 0;
                for (int i = 1; i < array.length; i++) {
                    if (array[i] > array[largest]) largest = i;
                }
                return largest;
            }

            @Override
            public boolean onCreateOptionsMenu (Menu menu){
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.settings, menu);
                return true;
            }

            @Override
            public boolean onOptionsItemSelected (@NonNull MenuItem item){
                if (item.getItemId() == R.id.ClearText) {
                    resultTextView.setText(" ");
                    return true;
                }
                return super.onOptionsItemSelected(item);
            }
        }
