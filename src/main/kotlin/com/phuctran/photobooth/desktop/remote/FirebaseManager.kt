package com.phuctran.photobooth.desktop.remote

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

object FirebaseManager {
    
    // Configurable via env vars or fixed file
    private const val CREDENTIALS_PATH = "serviceAccountKey.json"
    private const val STORAGE_BUCKET = "photop-bf902.appspot.com"
    
    private var isInitialized = false

    fun initialize(credentialsStream: InputStream? = null, storageBucketUrl: String? = null) {
        if (isInitialized) return
        
        try {
            val stream = credentialsStream ?: FileInputStream(CREDENTIALS_PATH)
            val optionsBuilder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                
            storageBucketUrl?.let {
                optionsBuilder.setStorageBucket(it)
            } ?: run {
                optionsBuilder.setStorageBucket(STORAGE_BUCKET)
            }

            FirebaseApp.initializeApp(optionsBuilder.build())
            isInitialized = true
            println("Firebase Admin SDK initialized successfully!")
        } catch (e: Exception) {
            println("Warning: Failed to initialize Firebase. ${e.message}")
        }
    }

    /**
     * Uploads the layout data to Firestore.
     */
    fun uploadLayout(layoutId: String, layoutData: Map<String, Any>) {
        if (!isInitialized) {
            println("Firebase is not initialized. Cannot upload layout.")
            return
        }

        try {
            val db = FirestoreClient.getFirestore()
            val docRef = db.collection("layouts").document(layoutId)
            val result = docRef.set(layoutData).get()
            println("Layout uploaded at: ${result.updateTime}")
        } catch (e: Exception) {
            println("Error uploading layout: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Uploads a PNG frame image to Firebase Storage, then saves the metadata to Firestore.
     */
    fun uploadFrameMetadata(frameId: String, layoutId: String, publicUrl: String) {
        if (!isInitialized) {
            println("Firebase is not initialized. Cannot upload frame metadata.")
            return
        }

        try {
            // Save metadata to Firestore
            val db = FirestoreClient.getFirestore()
            val frameData = mapOf(
                "layoutId" to layoutId,
                "imageUrl" to publicUrl,
                "name" to frameId,
                "createdAt" to System.currentTimeMillis()
            )
            
            val docRef = db.collection("frames").document(frameId)
            val result = docRef.set(frameData).get()
            
            println("Frame uploaded successfully to Cloudinary and metadata saved to Firestore!")
            println("Firestore document updated at: ${result.updateTime}")

        } catch (e: Exception) {
            println("Error saving frame metadata: ${e.message}")
            e.printStackTrace()
        }
    }
}
