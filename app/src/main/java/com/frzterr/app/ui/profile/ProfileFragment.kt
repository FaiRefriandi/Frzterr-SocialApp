package com.frzterr.app.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.local.ProfileLocalStore
import com.frzterr.app.data.remote.supabase.SupabaseManager
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.user.AppUser
import com.frzterr.app.data.repository.user.UserRepository
import com.frzterr.app.databinding.FragmentProfileBinding
import com.frzterr.app.ui.auth.AuthActivity
import com.yalantis.ucrop.UCrop
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = AuthRepository()
    private val userRepo = UserRepository()
    private val profileVM: ProfileViewModel by activityViewModels()

    // ================= IMAGE PICK =================
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { startCrop(it) }
        }

    private val cropResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                UCrop.getOutput(result.data!!)?.let { uploadAvatar(it) }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        // ======================================================
        // 🔥 CHECK IF USER CHANGED (LOGIN/LOGOUT)
        // ======================================================
        val currentUserId = runBlocking { 
            authRepo.getCurrentUser()?.id 
        }
        
        // Clear cache if user changed
        if (profileVM.cachedUser != null && profileVM.cachedUser?.id != currentUserId) {
            profileVM.cachedUser = null
            profileVM.lastRenderedAvatarUrl = null
        }

        // ======================================================
        // 🔥 LOAD LOCAL AVATAR FIRST (SYNC, NO DELAY)
        // ======================================================
        loadLocalAvatarSync()

        // ======================================================
        // 🔥 LOAD LOCAL STATE JIKA VIEWMODEL KOSONG
        // ======================================================
        if (profileVM.cachedUser == null) {
            val (name, avatarUrl, username) =
                ProfileLocalStore.load(requireContext())

            profileVM.cachedUser = AppUser(
                id = "local",
                fullName = name,
                avatarUrl = avatarUrl,
                username = username ?: "",
                usernameLower = username?.lowercase() ?: ""
            )
        }

        // ======================================================
        // 🔥 BIND STATE SECEPAT MUNGKIN (NAME & USERNAME ONLY)
        // ======================================================
        profileVM.cachedUser?.let {
            bindProfileText(it)
        }

        // ======================================================
        // 🔄 SYNC DB DI BACKGROUND (TANPA LOADING) - ALWAYS LOAD TO REFRESH AVATAR
        // ======================================================
        loadProfile(force = true, showLoading = false)

        binding.root.post { updateStatusBarIconColor() }

        // ======================================================
        // 🔥 SETUP ViewPager2 WITH SWIPEABLE TABS - INSTANT LOAD
        // ======================================================
        val postsAdapter = com.frzterr.app.ui.home.PostAdapter(
            onLikeClick = { postWithUser ->
                profileVM.toggleLike(postWithUser.post.id, postWithUser.isLiked)
            },
            onCommentClick = { postWithUser ->
                val commentsBottomSheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    onCommentAdded = {
                        profileVM.cachedUser?.id?.let { userId ->
                            profileVM.loadUserPosts(userId)
                        }
                    }
                )
                commentsBottomSheet.show(
                    childFragmentManager,
                    com.frzterr.app.ui.comments.CommentsBottomSheet.TAG
                )
            },
            onRepostClick = { postWithUser ->
                profileVM.toggleRepost(postWithUser.post.id, postWithUser.isReposted)
            },
            onUserClick = { postWithUser ->
                // Already on profile page
            }
        )

        val repostsAdapter = com.frzterr.app.ui.home.PostAdapter(
            onLikeClick = { postWithUser ->
                profileVM.toggleLike(postWithUser.post.id, postWithUser.isLiked)
            },
            onCommentClick = { postWithUser ->
                val commentsBottomSheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    onCommentAdded = {
                        profileVM.cachedUser?.id?.let { userId ->
                            profileVM.loadUserReposts(userId)
                        }
                    }
                )
                commentsBottomSheet.show(
                    childFragmentManager,
                    com.frzterr.app.ui.comments.CommentsBottomSheet.TAG
                )
            },
            onRepostClick = { postWithUser ->
                profileVM.toggleRepost(postWithUser.post.id, postWithUser.isReposted)
            },
            onUserClick = { postWithUser ->
                // Already on profile page
            }
        )

        // Create tab fragments list
        val fragments = mutableListOf<ProfileTabFragment>()
        
        // Setup ViewPager2 adapter
        val pagerAdapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): androidx.fragment.app.Fragment {
                val fragment = when (position) {
                    0 -> ProfileTabFragment.newInstance(ProfileTabFragment.TabType.POSTS)
                    1 -> ProfileTabFragment.newInstance(ProfileTabFragment.TabType.REPOSTS)
                    else -> ProfileTabFragment.newInstance(ProfileTabFragment.TabType.POSTS)
                }
                fragments.add(fragment)
                return fragment
            }
        }
        
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1 // Keep both tabs in memory
        
        // Connect TabLayout with ViewPager2
        com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.icon = when (position) {
                0 -> context?.getDrawable(R.drawable.tab_posts_icon)
                1 -> context?.getDrawable(R.drawable.tab_reposts_icon)
                else -> null
            }
        }.attach()
        
        // Set icon size programmatically
        binding.tabLayout.post {
            for (i in 0 until binding.tabLayout.tabCount) {
                val tab = binding.tabLayout.getTabAt(i)
                val iconSize = (25 * resources.displayMetrics.density).toInt() // 28dp to px
                tab?.icon?.setBounds(0, 0, iconSize, iconSize)
            }
        }

        // Observe user posts - UPDATE IMMEDIATELY
        profileVM.userPosts.observe(viewLifecycleOwner) { posts ->
            postsAdapter.submitList(posts)
            
            // Update empty state for Posts tab
            binding.viewPager.post {
                val postsFragment = childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment
                postsFragment?.updateEmptyState(posts?.isEmpty() ?: true)
            }
        }

        // Observe user reposts - UPDATE IMMEDIATELY
        profileVM.userReposts.observe(viewLifecycleOwner) { reposts ->
            repostsAdapter.submitList(reposts)
            
            // Update empty state for Reposts tab
            binding.viewPager.post {
                val repostsFragment = childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment
                repostsFragment?.updateEmptyState(reposts?.isEmpty() ?: true)
            }
        }

        // 🚀 ALWAYS LOAD DATA - ViewModel init sudah start, ini just ensures fresh data
        lifecycleScope.launch {
            val currentUser = authRepo.getCurrentUser()
            currentUser?.let { user ->
                // If ViewModel hasn't loaded yet or needs refresh
                if (profileVM.cachedUser == null) {
                    profileVM.cachedUser = userRepo.getUserByIdForce(user.id)
                }
                
                // Trigger load (will be fast if already loaded by init block)
                profileVM.cachedUser?.id?.let { userId ->
                    profileVM.loadUserPosts(userId)
                    profileVM.loadUserReposts(userId)
                }
            }
        }

        // Set adapters after a slight delay to ensure fragments are created
        binding.viewPager.postDelayed({
            val postsFragment = childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment
            val repostsFragment = childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment
            
            postsFragment?.setAdapter(postsAdapter)
            repostsFragment?.setAdapter(repostsAdapter)
            
            // Trigger initial update
            postsFragment?.updateEmptyState(profileVM.userPosts.value?.isEmpty() ?: true)
            repostsFragment?.updateEmptyState(profileVM.userReposts.value?.isEmpty() ?: true)
        }, 100)

        binding.imgAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        // Menu button (overflow) with logout option
        binding.btnMenu.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(R.menu.menu_profile, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_logout -> {
                        ProfileLocalStore.clear(requireContext())
                        lifecycleScope.launch {
                            authRepo.signOut()
                            startActivity(
                                Intent(requireContext(), AuthActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Edit Profile button (placeholder for now)
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        // Share Profile button
        binding.btnShareProfile.setOnClickListener {
            val username = profileVM.cachedUser?.username ?: "user"
            val shareText = "Check out @$username's profile!"
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Profil"))
        }
    }

    // ================= LOAD PROFILE =================
    private fun loadProfile(
        force: Boolean = false,
        showLoading: Boolean = false
    ) {
        if (!force && profileVM.cachedUser != null) return

        lifecycleScope.launch {
            try {

                val authUser = authRepo.getCurrentUser() ?: return@launch
                val dbUser = userRepo.getUserByIdForce(authUser.id) ?: return@launch

                profileVM.cachedUser = dbUser
                bindProfileText(dbUser)
                
                // Only update avatar if URL changed
                updateAvatarIfNeeded(dbUser.avatarUrl)

                ProfileLocalStore.save(
                    requireContext(),
                    dbUser.fullName,
                    dbUser.avatarUrl,
                    dbUser.username
                )


            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
            } finally {
            }
        }
    }
    
    // Variabel untuk track apakah gambar lokal sukses di-load
    private var isLocalAvatarLoaded = false
    private var localSavedAvatarUrl: String? = null

    // ================= LOAD AVATAR FROM LOCAL FILE =================
    private fun loadLocalAvatarSync() {
        // Load metadata dulu untuk dapat URL yang tersimpan
        val (name, avatarUrl, username) = ProfileLocalStore.load(requireContext())
        localSavedAvatarUrl = avatarUrl

        // 1. Coba load dari Coil Disk Cache dulu (untuk Network Image)
        if (avatarUrl != null) {
            val imageLoader = requireContext().imageLoader
            val snapshot = imageLoader.diskCache?.get(avatarUrl)
            if (snapshot != null) {
                // Ada di cache! Tampilkan file dari snapshot
                val file = snapshot.data.toFile()
                binding.imgAvatar.setImageURI(Uri.fromFile(file))
                binding.imgAvatar.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                isLocalAvatarLoaded = true
                return
            }
        }

        // 2. Fallback: Coba load dari Local File Path (untuk Image hasil Pick Gallery)
        val localAvatarPath = ProfileLocalStore.loadLocalAvatarPath(requireContext())
        if (localAvatarPath != null) {
            val file = File(localAvatarPath)
            if (file.exists()) {
                binding.imgAvatar.setImageURI(Uri.fromFile(file))
                binding.imgAvatar.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                isLocalAvatarLoaded = true // Tandai sukses
                return
            }
        }
        
        // 3. Gagal semua -> Placeholder
        binding.imgAvatar.setImageResource(R.drawable.ic_user_placeholder)
        binding.imgAvatar.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
        isLocalAvatarLoaded = false
    }
    
    // ================= UPDATE AVATAR IF URL CHANGED =================
    private fun updateAvatarIfNeeded(newAvatarUrl: String?) {
        // 🔥 ANTI-KEDIP LOGIC v2
        // Cek: Apakah URL baru dari server == URL yang kita simpan di lokal?
        // DAN apakah kita tadi sukses menampilkan gambar lokal?
        if (newAvatarUrl == localSavedAvatarUrl && isLocalAvatarLoaded) {
            // Gambar di layar SUDAH BENAR dan SUDAH TAMPIL.
            // Jangan load Coil sama sekali. Biarkan saja.
            // Ini 100% menghilangkan kedipan karena tidak ada layout pass / decoding ulang.
            return
        }

        // Kalau URL beda (user ganti foto di device lain) atau lokal tidak ada,
        // baru kita load pakai Coil.
        
        // Update rendered state
        profileVM.lastRenderedAvatarUrl = newAvatarUrl
        
        // URL changed, load from network with Coil
        binding.imgAvatar.load(newAvatarUrl) {
            crossfade(false)
            memoryCacheKey(newAvatarUrl)
            diskCacheKey(newAvatarUrl)
            size(512)

            placeholder(null) 
            
            if (newAvatarUrl == null) {
                error(R.drawable.ic_user_placeholder)
                fallback(R.drawable.ic_user_placeholder)
            } else {
                error(R.drawable.ic_user_placeholder)
            }
        }
    }

    // ================= BIND UI (TEXT ONLY) =================
    private fun bindProfileText(user: AppUser) {
        // Nama panggilan
        binding.tvName.text = user.fullName ?: ""

        // Username dengan awalan @
        binding.tvUsername.text =
            if (!user.username.isNullOrBlank()) {
                "@${user.username}"
            } else {
                ""
            }
    }

    // ================= BIND UI (LEGACY - WITH AVATAR) =================
    private fun bindProfile(user: AppUser) {
        bindProfileText(user)
        
        binding.imgAvatar.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)

        // ⛔ COIL BUKAN FIRST RENDER
        binding.imgAvatar.load(user.avatarUrl) {
            crossfade(false)
            size(256)

            if (user.avatarUrl == null) {
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            } else {
                placeholder(null)
                error(null)
            }
        }
    }

    // ================= START CROP =================
    private fun startCrop(source: Uri) {
        val dest = Uri.fromFile(
            File(requireContext().cacheDir, "crop_${UUID.randomUUID()}.jpg")
        )

        val intent = UCrop.of(source, dest)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .getIntent(requireContext())

        cropResult.launch(intent)
    }

    // ================= UPLOAD AVATAR =================
    private fun uploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            try {
                val user = authRepo.getCurrentUser() ?: return@launch
                val compressed = compressImage(uri)
                val fileName = "${user.id}.jpg"

                // ðŸ”¥ SIMPAN FILE LOKAL UNTUK FIRST RENDER
                val localFile = File(
                    requireContext().filesDir,
                    "avatar_${user.id}.jpg"
                )
                localFile.writeBytes(compressed)

                ProfileLocalStore.saveLocalAvatarPath(
                    requireContext(),
                    localFile.absolutePath
                )

                SupabaseManager.client.storage
                    .from("avatars")
                    .upload(
                        path = fileName,
                        data = compressed
                    ) {
                        upsert = true
                        contentType = io.ktor.http.ContentType.Image.JPEG
                    }

                val baseUrl = SupabaseManager.client.storage
                    .from("avatars")
                    .publicUrl(fileName)

                val versionedUrl = "$baseUrl?v=${System.currentTimeMillis()}"

                // DB = SINGLE SOURCE OF TRUTH
                authRepo.updateCustomAvatar(versionedUrl)
                userRepo.updateAvatarUrl(user.id, versionedUrl)

                profileVM.cachedUser =
                    profileVM.cachedUser?.copy(avatarUrl = versionedUrl)

                ProfileLocalStore.save(
                    requireContext(),
                    profileVM.cachedUser?.fullName,
                    versionedUrl,
                    profileVM.cachedUser?.username
                )

                bindProfile(profileVM.cachedUser!!)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal upload avatar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= COMPRESS IMAGE =================
    private suspend fun compressImage(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val bitmap =
                android.provider.MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    uri
                )

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            stream.toByteArray()
        }

    // ================= STATUS BAR =================
    private fun updateStatusBarIconColor() {
        val activity = activity ?: return
        val window = activity.window

        val bgColor = (binding.root.background as? ColorDrawable)?.color ?: Color.BLACK
        val isDark = isColorDark(bgColor)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (
                0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color)
                ) / 255
        return darkness >= 0.5
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}