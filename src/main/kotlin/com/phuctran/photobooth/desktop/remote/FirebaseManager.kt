package com.phuctran.photobooth.desktop.remote

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.LayoutSlot
import com.phuctran.photobooth.desktop.model.LayoutFamily

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
     * Deletes a layout from Firestore.
     */
    fun deleteLayout(layoutId: String) {
        if (!isInitialized) {
            println("Firebase is not initialized. Cannot delete layout.")
            return
        }

        try {
            val db = FirestoreClient.getFirestore()
            val docRef = db.collection("layouts").document(layoutId)
            docRef.delete().get()
            println("Layout $layoutId deleted successfully.")
        } catch (e: Exception) {
            println("Error deleting layout: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Updates specific configuration fields (price, shot count, countdown) of a layout.
     */
    fun updateLayoutConfig(layoutId: String, basePrice: Long, shotCount: Int, countdownSeconds: Int) {
        if (!isInitialized) {
            println("Firebase is not initialized. Cannot update layout config.")
            return
        }

        try {
            val db = FirestoreClient.getFirestore()
            val docRef = db.collection("layouts").document(layoutId)
            
            val updates = mapOf(
                "basePrice" to basePrice,
                "shotCount" to shotCount,
                "countdownSeconds" to countdownSeconds
            )
            
            val result = docRef.set(updates, SetOptions.merge()).get()
            println("Layout config updated at: ${result.updateTime}")
        } catch (e: Exception) {
            println("Error updating layout config: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Fetches layout data from Firestore and maps it to LayoutMode.
     */
    suspend fun fetchLayouts(): List<LayoutMode> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            println("Firebase is not initialized. Cannot fetch layouts.")
            return@withContext emptyList()
        }
        try {
            val db = FirestoreClient.getFirestore()
            val querySnapshot = db.collection("layouts").get().get() // get() on ApiFuture
            val layouts = mutableListOf<LayoutMode>()
            for (doc in querySnapshot.documents) {
                val id = doc.getString("id") ?: doc.id
                val width = doc.getDouble("width")?.toFloat() ?: doc.getLong("width")?.toFloat() ?: 1200f
                val height = doc.getDouble("height")?.toFloat() ?: doc.getLong("height")?.toFloat() ?: 1800f
                val slotsList = doc.get("slots") as? List<Map<String, Any>> ?: emptyList()
                val slots = slotsList.mapNotNull { slotMap ->
                    try {
                        val index = (slotMap["index"] as? Number)?.toInt() ?: 0
                        val x = (slotMap["x"] as? Number)?.toFloat() ?: 0f
                        val y = (slotMap["y"] as? Number)?.toFloat() ?: 0f
                        val w = (slotMap["width"] as? Number)?.toFloat() ?: 0.5f
                        val h = (slotMap["height"] as? Number)?.toFloat() ?: 0.5f
                        LayoutSlot(index, x, y, w, h)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.index }

                if (slots.isNotEmpty()) {
                            val pAspectRatio = doc.getDouble("printAspectRatio")?.toFloat() ?: (width / height)
                            val firstSlot = slots.firstOrNull()
                            val computedPhotoAspect = if (firstSlot != null) {
                                (firstSlot.width / firstSlot.height) * pAspectRatio
                            } else 1f
                            val photoAspect = doc.getDouble("photoAspectRatio")?.toFloat() ?: computedPhotoAspect
                            
                            layouts.add(
                                LayoutMode(
                                    id = id,
                                    title = doc.getString("title") ?: "Layout $id",
                                    subtitle = doc.getString("subtitle") ?: "${slots.size} ảnh",
                                    description = doc.getString("description") ?: "Kích thước ${width.toInt()}x${height.toInt()}",
                                    family = LayoutFamily.Grid,
                                    shotCount = doc.getLong("shotCount")?.toInt() ?: slots.size,
                                    selectCount = doc.getLong("selectCount")?.toInt() ?: slots.size,
                                    countdownSeconds = doc.getLong("countdownSeconds")?.toInt() ?: 3,
                                    basePrice = doc.getLong("basePrice") ?: 50000L,
                                    mediaLabel = doc.getString("mediaLabel") ?: "Layout $id",
                                    accentColor = doc.getLong("accentColor") ?: 0xFF4CAF50,
                                    absoluteSlots = slots,
                                    printAspectRatio = pAspectRatio,
                                    gridColumns = doc.getLong("gridColumns")?.toInt() ?: 1,
                                    printSizeLabel = doc.getString("printSizeLabel") ?: "15 x 10 cm",
                                    photoAspectRatio = photoAspect
                                )
                            )
                }
            }
            return@withContext layouts
        } catch (e: Exception) {
            println("Error fetching layouts: ${e.message}")
            e.printStackTrace()
            return@withContext emptyList()
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
