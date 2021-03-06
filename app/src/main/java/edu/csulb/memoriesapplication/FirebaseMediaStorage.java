package edu.csulb.memoriesapplication;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Created by Daniel on 11/30/2017.
 */

public class FirebaseMediaStorage {
    private final static String MEDIA = "media";
    private final String TAG = "FirebaseMediaStorage";
    private StorageReference mediaStorageReference;
    private String userId;

    FirebaseMediaStorage() {
        FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
        //Create a reference to the folder where every media should be saved
        mediaStorageReference = firebaseStorage.getReference().child(MEDIA);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        userId = firebaseAuth.getCurrentUser().getUid();
    }

    void saveImageToFirebaseStorage(Bitmap image, final String city, final String state){
        //Create the data that is going to be output to Firebase Storage
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 30, byteArrayOutputStream);
        byte[] byteOutput = byteArrayOutputStream.toByteArray();

        //Create a random string appended to the userId to create a unique String that symbolizes the user's video
        String randomString = userId + UUID.randomUUID().toString();
        //Remove the '-' character for easier storage and readability
        randomString = randomString.replaceAll("-", "");

        //Create the storage reference and upload it to that reference
        final StorageReference mediaStorageLoc = mediaStorageReference.child(randomString);
        UploadTask uploadTask = mediaStorageLoc.putBytes(byteOutput);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Uri downloadUri = taskSnapshot.getDownloadUrl();
                //Successful upload, get download URL to reference from user's database and global database
                UserDatabase.addMediaUrl(userId, downloadUri, city, state, UserDatabase.MediaType.IMAGE);
                //Adds the media URl to the global database
                GlobalDatabase.addMediaUrl(downloadUri, city, state, GlobalDatabase.MediaType.IMAGE);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                e.printStackTrace();
            }
        });
    }

    void saveVideoToFirebaseStorage(Uri video) {
        //Creates a random Unique string to symbolize the file video
        String randomString = UUID.randomUUID().toString();
        final StorageReference mediaStorageLoc = mediaStorageReference.child(randomString);
        UploadTask uploadTask = mediaStorageLoc.putFile(video);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Uri downloadUri = taskSnapshot.getDownloadUrl();
                //Successful upload, get download URL to reference from user's database and global database
//                UserDatabase.addMediaUrl(userId, downloadUri, UserDatabase.MediaType.VIDEO);
//                //Adds the media URl to the global database
//                GlobalDatabase.addMediaUrl(downloadUri, GlobalDatabase.MediaType.VIDEO);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                e.printStackTrace();
            }
        });
    }

}
