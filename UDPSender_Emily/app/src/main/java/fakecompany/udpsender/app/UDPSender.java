package fakecompany.udpsender.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by greenteawarrior on 3/10/14.
 *
 * Sets up the UDP socket to send information to the webapp (currently just a hello world string,
 * video data communication is a work in progress....)
 *
 * Should always be running in its own thread (not the UI or asynctask)
 */

public class UDPSender implements Runnable{
    private DatagramSocket socket;
    private MediaRecorder recorder;
    private String address_string = "192.168.48.198"; //use ip addr command in a linux terminal
    private int foreign_port = 8888;
    private int local_port = 8888;
    private InetAddress foreign_address;

    //camera things
    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;
    private static final String TAG = "CameraDemo";
    private boolean isRecording = false;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    @Override
    public void run() {
        try {
            foreign_address  = InetAddress.getByName(address_string);
            socket = new DatagramSocket(local_port);
            sendTestPacket();
            cleanShutdown();

            // Create an instance of Camera
            mCamera = getCameraInstance();

            // Create our Preview view and set it as the content of our activity.
            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

            // Checking some storage things
            if (! Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.d(TAG, Environment.getExternalStorageState());
                return;
            }

            Log.d(TAG, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
            Log.d(TAG, "it survived the external storage stage if statement");

            // Add a listener to the Capture button
            Button captureButton = (Button) findViewById(R.id.button_capture);
            captureButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (isRecording) {
                                // stop recording and release camera
                                mMediaRecorder.stop();  // stop the recording
                                releaseMediaRecorder(); // release the MediaRecorder object
                                mCamera.lock();         // take camera access back from MediaRecorder

                                // inform the user that recording has stopped
                                Log.d(TAG, "Stopped recording");
                                //this.setText("Record"); //we'll find some way to set the button label text...
                                isRecording = false;
                            } else {
                                // initialize video camera
                                if (prepareVideoRecorder()) {
                                    // Camera is available and unlocked, MediaRecorder is prepared,
                                    // now you can start recording
                                    mMediaRecorder.start();

                                    // inform the user that recording has started
                                    Log.d(TAG, "Start recording");
                                    //this.setText("Recording~"); //we'll find some way to set the button label text...
                                    isRecording = true;
                                } else {
                                    // prepare didn't work, release the camera
                                    releaseMediaRecorder();
                                    // inform user
                                }
                            }
                        }
                    }
            );



        } catch (Exception e) {
            e.printStackTrace();
            cleanShutdown();
        }
    }


    private void sendTestPacket() throws IOException {
        String message = "hello world";
        byte[] data = message.getBytes();
        Log.d("UDP", "trying to hello world");
        DatagramPacket packet = new DatagramPacket(data, data.length, foreign_address, foreign_port);
        socket.send(packet);

    }

    private void cleanShutdown() {
        if (socket != null) {
            socket.close();
            Log.d("UDP", "socket closed");
        }
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.e(TAG, Log.getStackTraceString(e));

        }
        return c; // returns null if camera is unavailable
    }

    private boolean prepareVideoRecorder(){

        //mCamera = getCameraInstance();
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }


    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "UDPSender_Emily");
        Log.d("UDPSender_Emily", "starting to save the video file");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("UDPSender_Emily", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }



}
