package com.sangithasubash.treadscan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 200;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int STORAGE_PERMISSION_CODE = 102;

    private ImageView imageIv;
    private TextView resultIv;
    private Bitmap selectedImageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show();
        }

        imageIv = findViewById(R.id.imageIv);
        resultIv = findViewById(R.id.resultIv);

        MaterialButton cameraBtn = findViewById(R.id.cameraBtn);
        MaterialButton galleryBtn = findViewById(R.id.GalleryBtn);
        MaterialButton scanBtn = findViewById(R.id.scanBtn);

        cameraBtn.setOnClickListener(view -> checkCameraPermission());
        galleryBtn.setOnClickListener(view -> checkStoragePermission());
        scanBtn.setOnClickListener(view -> performTreadScan());
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above, use MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                openGallery();
            } else {
                // Request permission using Settings
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, STORAGE_PERMISSION_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            }
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE) {
                Bundle extras = data.getExtras();
                selectedImageBitmap = (Bitmap) extras.get("data");
                imageIv.setImageBitmap(selectedImageBitmap);

                // Notify media scanner
                saveImageAndNotify(selectedImageBitmap);

            } else if (requestCode == GALLERY_REQUEST_CODE) {
                Uri imageUri = data.getData();
                try {
                    InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    selectedImageBitmap = BitmapFactory.decodeStream(imageStream);
                    imageIv.setImageBitmap(selectedImageBitmap);

                    // Notify media scanner
                    saveImageAndNotify(selectedImageBitmap);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveImageAndNotify(Bitmap bitmap) {
        String filePath = Environment.getExternalStorageDirectory() + "/sdcard/Download"; // Change the filename as needed
        File file = new File(filePath);

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // Save the image
            // Notify the media scanner
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null,
                    (path, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Optional: Show a Toast or update the UI
        Toast.makeText(this, "Image saved and media scanner notified.", Toast.LENGTH_SHORT).show();
    }
    private void performTreadScan() {
        if (selectedImageBitmap == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert Bitmap to Mat (OpenCV image format)
        Mat originalMat = new Mat();
        Utils.bitmapToMat(selectedImageBitmap, originalMat);

        // Step 1: Convert to grayscale for better edge detection
        Mat grayImage = new Mat();
        Imgproc.cvtColor(originalMat, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Step 2: Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);

        // Step 3: Edge detection using Canny
        Mat edges = new Mat();
        Imgproc.Canny(grayImage, edges, 50, 150); // Adjust the thresholds if necessary

        // Step 4: Find contours from edges
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Step 5: Filter contours to focus on grooves
        List<MatOfPoint> grooveContours = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 50) {  // Filter out small contours (tweak value as needed)
                grooveContours.add(contour);
            }
        }

        // Draw the filtered contours for visualization (optional)
        Mat contourImage = originalMat.clone();
        Imgproc.drawContours(contourImage, grooveContours, -1, new Scalar(0, 255, 0), 2);

        // Step 6: Calculate average depth based on contour bounding rectangles
        double totalDepth = 0;
        int validContours = 0;

        for (MatOfPoint contour : grooveContours) {
            Rect boundingBox = Imgproc.boundingRect(contour);

            // Filter based on bounding box height for groove detection
            if (boundingBox.height > 10 && boundingBox.height < 100) { // Adjust range based on testing
                totalDepth += boundingBox.height;
                validContours++;
            }
        }

        // Calculate estimated depth
        double estimatedDepthPixels = validContours > 0 ? (totalDepth / validContours) : 0;

        // Step 7: Convert pixels to mm using a reference (e.g., 10 pixels per mm)
        double pixelsPerMm = 10;  // You need to calibrate this value with a known reference
        double estimatedDepthMm = estimatedDepthPixels / pixelsPerMm;

        // Convert mm to inches (1 mm = 0.03937 inches)
        double estimatedDepthInches = estimatedDepthMm * 0.03937;

        // Display the result in both mm and inches
        String treadDepthResult = String.format("Estimated Tread Depth: %.2f mm (%.2f inches)", estimatedDepthMm, estimatedDepthInches);
        resultIv.setText(treadDepthResult);

        // Optional: Show the contour image for debugging
        Bitmap contourBitmap = Bitmap.createBitmap(contourImage.cols(), contourImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(contourImage, contourBitmap);
        imageIv.setImageBitmap(contourBitmap);
    }







    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}