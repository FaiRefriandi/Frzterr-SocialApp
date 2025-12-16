package com.frzterr.app.data.repository.post

import android.util.Log
import com.frzterr.app.data.model.Like
import com.frzterr.app.data.model.Post
import com.frzterr.app.data.model.PostWithUser
import com.frzterr.app.data.remote.supabase.SupabaseManager
import com.frzterr.app.data.repository.user.AppUser
import com.frzterr.app.data.repository.user.UserRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PostRepository {

    private val postgrest get() = SupabaseManager.client.postgrest
    private val storage get() = SupabaseManager.client.storage
    private val userRepo = UserRepository()

    // ========================================================================
    // GET POSTS
    // ========================================================================

    /**
     * Get all posts with user data, ordered by newest first
     */
    suspend fun getAllPosts(currentUserId: String): List<PostWithUser> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PostRepository", "Fetching all posts...")

                // Get all posts
                val posts = postgrest["posts"]
                    .select()
                    .decodeList<Post>()

                Log.d("PostRepository", "Fetched ${posts.size} posts")

                if (posts.isEmpty()) {
                    return@withContext emptyList()
                }

                val postIds = posts.map { it.id }

                // Get all likes for these posts
                val allLikes = postgrest["likes"]
                    .select()
                    .decodeList<Like>()

                // Get all comments for these posts
                val allComments = postgrest["comments"]
                    .select()
                    .decodeList<com.frzterr.app.data.model.Comment>()

                // Get all reposts for these posts
                val allReposts = postgrest["reposts"]
                    .select()
                    .decodeList<com.frzterr.app.data.model.Repost>()

                // Count likes per post
                val likesPerPost = allLikes.groupBy { it.postId }
                val commentsPerPost = allComments.groupBy { it.postId }
                val repostsPerPost = allReposts.groupBy { it.postId }

                // Get user likes for isLiked status
                val likedPostIds = allLikes
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                // Get user reposts for isReposted status
                val repostedPostIds = allReposts
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                // Get unique user IDs
                val userIds = posts.map { it.userId }.distinct()

                // Fetch all users
                val users = mutableMapOf<String, AppUser>()
                userIds.forEach { userId ->
                    userRepo.getUserById(userId)?.let { user ->
                        users[userId] = user
                    }
                }

                // Combine posts with real-time counts
                val postsWithUser = posts.mapNotNull { post ->
                    users[post.userId]?.let { user ->
                        val actualLikeCount = likesPerPost[post.id]?.size ?: 0
                        val actualCommentCount = commentsPerPost[post.id]?.size ?: 0
                        val actualRepostCount = repostsPerPost[post.id]?.size ?: 0

                        // Update post with real counts
                        val updatedPost = post.copy(
                            likeCount = actualLikeCount,
                            commentCount = actualCommentCount,
                            repostCount = actualRepostCount
                        )

                        PostWithUser(
                            post = updatedPost,
                            user = user,
                            isLiked = likedPostIds.contains(post.id),
                            isReposted = repostedPostIds.contains(post.id)
                        )
                    }
                }.sortedByDescending { it.post.createdAt }

                Log.d("PostRepository", "Mapped ${postsWithUser.size} posts with real-time counts")

                postsWithUser
            } catch (e: Exception) {
                Log.e("PostRepository", "Error fetching posts", e)
                emptyList()
            }
        }

    /**
     * Get posts from a specific user
     */
    suspend fun getUserPosts(userId: String, currentUserId: String): List<PostWithUser> =
        withContext(Dispatchers.IO) {
            try {
                val posts = postgrest["posts"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Post>()

                if (posts.isEmpty()) {
                    return@withContext emptyList()
                }

                val postIds = posts.map { it.id }

                // 🚀 OPTIMIZED: Get ONLY likes, comments, and reposts for THIS USER'S POSTS
                val allLikes = if (postIds.isNotEmpty()) {
                    postgrest["likes"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<Like>()
                } else emptyList()

                val allComments = if (postIds.isNotEmpty()) {
                    postgrest["comments"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<com.frzterr.app.data.model.Comment>()
                } else emptyList()

                val allReposts = if (postIds.isNotEmpty()) {
                    postgrest["reposts"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<com.frzterr.app.data.model.Repost>()
                } else emptyList()

                // Count per post
                val likesPerPost = allLikes.groupBy { it.postId }
                val commentsPerPost = allComments.groupBy { it.postId }
                val repostsPerPost = allReposts.groupBy { it.postId }
                
                val likedPostIds = allLikes
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                val repostedPostIds = allReposts
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                val user = userRepo.getUserById(userId)
                    ?: return@withContext emptyList()

                posts.map { post ->
                    val actualLikeCount = likesPerPost[post.id]?.size ?: 0
                    val actualCommentCount = commentsPerPost[post.id]?.size ?: 0
                    val actualRepostCount = repostsPerPost[post.id]?.size ?: 0

                    val updatedPost = post.copy(
                        likeCount = actualLikeCount,
                        commentCount = actualCommentCount,
                        repostCount = actualRepostCount
                    )

                    PostWithUser(
                        post = updatedPost,
                        user = user,
                        isLiked = likedPostIds.contains(post.id),
                        isReposted = repostedPostIds.contains(post.id)
                    )
                }.sortedByDescending { it.post.createdAt }
            } catch (e: Exception) {
                Log.e("PostRepository", "Error fetching user posts", e)
                emptyList()
            }
        }

    private suspend fun getLikedPostIds(userId: String, postIds: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            try {
                if (postIds.isEmpty()) return@withContext emptySet()

                val likes = postgrest["likes"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Like>()

                likes.map { it.postId }.toSet()
            } catch (e: Exception) {
                Log.e("PostRepository", "Error fetching likes", e)
                emptySet()
            }
        }

    // ========================================================================
    // CREATE POST
    // ========================================================================

    suspend fun createPost(
        userId: String,
        content: String,
        imageUrl: String? = null
    ): Result<Post> = withContext(Dispatchers.IO) {
        try {
            Log.d("PostRepository", "Creating post for user: $userId")

            val payload = mutableMapOf(
                "user_id" to userId,
                "content" to content
            )

            if (imageUrl != null) {
                payload["image_url"] = imageUrl
            }

            postgrest["posts"].insert(payload)

            Log.d("PostRepository", "Post created successfully")
            Result.success(Post(
                id = UUID.randomUUID().toString(),
                userId = userId,
                content = content,
                imageUrl = imageUrl,
                createdAt = "",
                likeCount = 0,
                commentCount = 0,
                repostCount = 0
            ))
        } catch (e: Exception) {
            Log.e("PostRepository", "Error creating post", e)
            Result.failure(e)
        }
    }

    /**
     * Upload image to Supabase Storage and return public URL
     */
    suspend fun uploadPostImage(userId: String, imageBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = "${userId}_${System.currentTimeMillis()}.jpg"

                storage.from("post_images")
                    .upload(
                        path = fileName,
                        data = imageBytes
                    ) {
                        upsert = false
                        contentType = io.ktor.http.ContentType.Image.JPEG
                    }

                val publicUrl = storage.from("post_images").publicUrl(fileName)

                Log.d("PostRepository", "Image uploaded: $publicUrl")
                Result.success(publicUrl)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error uploading image", e)
                Result.failure(e)
            }
        }

    // ========================================================================
    // DELETE POST
    // ========================================================================

    suspend fun deletePost(postId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postgrest["posts"]
                    .delete {
                        filter {
                            eq("id", postId)
                            eq("user_id", userId)
                        }
                    }

                Log.d("PostRepository", "Post deleted: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error deleting post", e)
                Result.failure(e)
            }
        }

    // ========================================================================
    // LIKE / UNLIKE
    // ========================================================================

    suspend fun likePost(postId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PostRepository", "Liking post: $postId by user: $userId")
                
                val payload = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )

                postgrest["likes"].insert(payload)

                Log.d("PostRepository", "Post liked successfully: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error liking post: $postId", e)
                Result.failure(e)
            }
        }

    suspend fun unlikePost(postId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PostRepository", "Unliking post: $postId by user: $userId")
                
                postgrest["likes"]
                    .delete {
                        filter {
                            eq("post_id", postId)
                            eq("user_id", userId)
                        }
                    }

                Log.d("PostRepository", "Post unliked successfully: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error unliking post: $postId", e)
                Result.failure(e)
            }
        }


    /**
     * Toggle like status for a post
     */
    suspend fun toggleLike(postId: String, userId: String, currentlyLiked: Boolean): Result<Boolean> {
        return if (currentlyLiked) {
            unlikePost(postId, userId).map { false }
        } else {
            likePost(postId, userId).map { true }
        }
    }

    // ========================================================================
    // COMMENTS
    // ========================================================================

    suspend fun getPostComments(postId: String): List<com.frzterr.app.data.model.CommentWithUser> =
        withContext(Dispatchers.IO) {
            try {
                val comments = postgrest["comments"]
                    .select {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                    .decodeList<com.frzterr.app.data.model.Comment>()

                // Get unique user IDs
                val userIds = comments.map { it.userId }.distinct()

                // Fetch users
                val users = mutableMapOf<String, AppUser>()
                userIds.forEach { userId ->
                    userRepo.getUserById(userId)?.let { user ->
                        users[userId] = user
                    }
                }

                // Combine comments with user data
                comments.mapNotNull { comment ->
                    users[comment.userId]?.let { user ->
                        com.frzterr.app.data.model.CommentWithUser(
                            comment = comment,
                            user = user
                        )
                    }
                }.sortedBy { it.comment.createdAt }
            } catch (e: Exception) {
                Log.e("PostRepository", "Error fetching comments", e)
                emptyList()
            }
        }

    suspend fun addComment(postId: String, userId: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val payload = mapOf(
                    "post_id" to postId,
                    "user_id" to userId,
                    "content" to content
                )

                postgrest["comments"].insert(payload)

                // Manual update comment_count - fetch current and increment
                try {
                    val currentPost = postgrest["posts"]
                        .select {
                            filter { eq("id", postId) }
                        }
                        .decodeSingle<Post>()

                    postgrest["posts"]
                        .update({
                            set("comment_count", currentPost.commentCount + 1)
                        }) {
                            filter { eq("id", postId) }
                        }
                } catch (e: Exception) {
                    Log.w("PostRepository", "Failed to update comment_count, will sync on refresh", e)
                }

                Log.d("PostRepository", "Comment added to post: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error adding comment", e)
                Result.failure(e)
            }
        }

    // ========================================================================
    // REPOST / UNREPOST
    // ========================================================================

    suspend fun repostPost(postId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PostRepository", "Reposting post: $postId by user: $userId")
                
                val payload = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )

                postgrest["reposts"].insert(payload)

                Log.d("PostRepository", "Post reposted successfully: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error reposting post: $postId", e)
                Result.failure(e)
            }
        }

    suspend fun unrepostPost(postId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PostRepository", "Unreposting post: $postId by user: $userId")
                
                postgrest["reposts"]
                    .delete {
                        filter {
                            eq("post_id", postId)
                            eq("user_id", userId)
                        }
                    }

                Log.d("PostRepository", "Post unreposted successfully: $postId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("PostRepository", "Error unreposting post: $postId", e)
                Result.failure(e)
            }
        }

    /**
     * Toggle repost status for a post
     */
    suspend fun toggleRepost(postId: String, userId: String, currentlyReposted: Boolean): Result<Boolean> {
        return if (currentlyReposted) {
            unrepostPost(postId, userId).map { false }
        } else {
            repostPost(postId, userId).map { true }
        }
    }

    /**
     * Get posts that a user has reposted
     */
    suspend fun getUserReposts(userId: String, currentUserId: String): List<PostWithUser> =
        withContext(Dispatchers.IO) {
            try {
                // Get all reposts by this user
                val reposts = postgrest["reposts"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<com.frzterr.app.data.model.Repost>()

                if (reposts.isEmpty()) {
                    return@withContext emptyList()
                }

                // Get all posts that were reposted
                val posts = postgrest["posts"]
                    .select()
                    .decodeList<Post>()
                    .filter { post -> reposts.any { it.postId == post.id } }

                if (posts.isEmpty()) {
                    return@withContext emptyList()
                }

                val postIds = posts.map { it.id }

                // 🚀 OPTIMIZED: Get ONLY likes, comments, and reposts for REPOSTED POSTS
                val allLikes = if (postIds.isNotEmpty()) {
                    postgrest["likes"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<Like>()
                } else emptyList()

                val allComments = if (postIds.isNotEmpty()) {
                    postgrest["comments"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<com.frzterr.app.data.model.Comment>()
                } else emptyList()

                val allReposts = if (postIds.isNotEmpty()) {
                    postgrest["reposts"]
                        .select {
                            filter {
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<com.frzterr.app.data.model.Repost>()
                } else emptyList()

                // Count per post
                val likesPerPost = allLikes.groupBy { it.postId }
                val commentsPerPost = allComments.groupBy { it.postId }
                val repostsPerPost = allReposts.groupBy { it.postId }
                
                val likedPostIds = allLikes
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                val repostedPostIds = allReposts
                    .filter { it.userId == currentUserId }
                    .map { it.postId }
                    .toSet()

                // Get unique user IDs
                val userIds = posts.map { it.userId }.distinct()

                // Fetch users
                val users = mutableMapOf<String, AppUser>()
                userIds.forEach { uid ->
                    userRepo.getUserById(uid)?.let { user ->
                        users[uid] = user
                    }
                }

                // Combine posts with real-time counts
                posts.mapNotNull { post ->
                    users[post.userId]?.let { user ->
                        val actualLikeCount = likesPerPost[post.id]?.size ?: 0
                        val actualCommentCount = commentsPerPost[post.id]?.size ?: 0
                        val actualRepostCount = repostsPerPost[post.id]?.size ?: 0

                        val updatedPost = post.copy(
                            likeCount = actualLikeCount,
                            commentCount = actualCommentCount,
                            repostCount = actualRepostCount
                        )

                        PostWithUser(
                            post = updatedPost,
                            user = user,
                            isLiked = likedPostIds.contains(post.id),
                            isReposted = repostedPostIds.contains(post.id)
                        )
                    }
                }.sortedByDescending { it.post.createdAt }
            } catch (e: Exception) {
                Log.e("PostRepository", "Error fetching user reposts", e)
                emptyList()
            }
        }
}
