package com.frzterr.app.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import com.google.android.material.appbar.AppBarLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
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

    private lateinit var postsAdapter: com.frzterr.app.ui.home.PostAdapter
    private lateinit var repostsAdapter: com.frzterr.app.ui.home.PostAdapter
    
    // Flag to prevent saving offset during restore
    private var isRestoringOffset = false

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

        // 🚀 POSTPONE transition to wait for data and layout (prevents stutter/lag)
        postponeEnterTransition()

        // ======================================================
        // 🔥 CHECK PROFILE MODE (MY PROFILE vs PUBLIC PROFILE)
        // ======================================================
        // ======================================================
        // 🔥 CHECK PROFILE MODE (MY PROFILE vs PUBLIC PROFILE)
        // ======================================================
        val currentUser = authRepo.getCurrentUser()
        val currentUserId = currentUser?.id
        val argUserId = arguments?.getString("userId")
        
        // If argUserId is present and different from current, it's a Public Profile
        val targetUserId = if (argUserId != null && argUserId != currentUserId) argUserId else currentUserId //?: return
        
        if (targetUserId == null) {
            // Guard clause: If no user ID found at all (shouldn't happen if auth working), just return
            return
        }

        // 🚀 Initialize ViewPager & Adapters BEFORE accessing them (Fixes lateinit crash)
        setupViewPager(targetUserId)

        // Ensure adapters restore scroll position correctly
        postsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        repostsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        val isMyProfile = targetUserId == currentUserId
        
        // 🔥 INSTANT AVATAR LOAD (From passed arguments)
        val argAvatarUrl = arguments?.getString("avatarUrl")
        if (!argAvatarUrl.isNullOrEmpty() && profileVM.lastRenderedAvatarUrl != argAvatarUrl) {
            binding.shimmerAvatar.visibility = View.VISIBLE
            binding.shimmerAvatar.startShimmer()
            
            binding.imgAvatar.load(argAvatarUrl) {
                crossfade(false)
                listener(
                    onSuccess = { _, _ ->
                        binding.shimmerAvatar.stopShimmer()
                        binding.shimmerAvatar.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        binding.shimmerAvatar.stopShimmer()
                        binding.shimmerAvatar.visibility = View.GONE
                    }
                )
            }
            // Mark as rendered
            profileVM.lastRenderedAvatarUrl = argAvatarUrl
        } else {
             // If no new URL passed, don't show shimmer at start
             binding.shimmerAvatar.visibility = View.GONE
        }

        // ======================================================
        // 🔥 SETUP UI BASED ON MODE
        // ======================================================
        if (isMyProfile) {
            setupMyProfileUI()
            // 🚀 LOAD LOCAL AVATAR IMMEDIATELY (Prevents Flash)
            // Use lifecycleScope to avoid Main Thread Disk I/O
            lifecycleScope.launch(Dispatchers.IO) {
                loadLocalAvatarSync()
            }
        } else {
            setupPublicProfileUI(targetUserId)
        }

        // ======================================================
        // 🔥 LOAD DATA (OPTIMIZED: Use cache if possible)
        // ======================================================
        val isSameUser = profileVM.user.value?.id == targetUserId
        
        if (!isSameUser) {
            // Only clear if switching to a DIFFERENT user
            profileVM.lastRenderedAvatarUrl = null
            profileVM.clearData()
        }

        // 🚀 OBSERVE USER DATA (Instant & Real-time updates)
        profileVM.user.observe(viewLifecycleOwner) { user ->
            if (user != null && user.id == targetUserId) {
                bindProfileText(user)
                updateAvatarIfNeeded(user.avatarUrl)
            }
        }

        // Load/Refresh from network
        loadProfile(targetUserId, force = !isSameUser)
        
        // 3. Load Posts & Reposts (Optimized in VM to handled duplicates if needed)
        profileVM.loadUserPosts(targetUserId)
        profileVM.loadUserReposts(targetUserId)
        
        // 4. Load follow data
        profileVM.loadFollowData(targetUserId)
        
        // Observe Follow Status (Only for Public Profile)
        if (!isMyProfile) {
            profileVM.isFollowing.observe(viewLifecycleOwner) { isFollowing ->
                updateFollowButton(isFollowing)
            }
        }
        
        // Observe Follow Counts
        // Observe Follow Counts
        profileVM.followerCount.observe(viewLifecycleOwner) { count ->
             binding.tvFollowersCount.text = formatCount(count)
        }
        
        profileVM.followingCount.observe(viewLifecycleOwner) { count ->
             binding.tvFollowingCount.text = formatCount(count)
        }

        binding.root.post { updateStatusBarIconColor() }

        // ======================================================
        // 🔥 APPBAR OFFSET (Scroll State Persistence)
        // ======================================================
        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            // Save current offset to ViewModel ONLY if not currently restoring
            if (!isRestoringOffset) {
                profileVM.appBarOffset = verticalOffset
            }
        })
        
        // Initial restore attempt in onViewCreated with delay
        restoreAppBarOffset()

        // ======================================================
        // 🔥 CLICK LISTENERS (COMMON)
        // ======================================================
        binding.imgAvatar.setOnClickListener {
            val avatarUrl = profileVM.user.value?.avatarUrl
            AvatarPreviewDialogFragment.newInstance(avatarUrl)
                .show(requireActivity().supportFragmentManager, AvatarPreviewDialogFragment.TAG)
        }
        
        // Menu button - Hide for public profile for now, or show report option later
        binding.btnMenu.visibility = if (isMyProfile) View.VISIBLE else View.GONE
        if (isMyProfile) {
            binding.btnMenu.setOnClickListener {
                ProfileOptionsBottomSheet(
                    onLogoutClick = {
                        ProfileLocalStore.clear(requireContext())
                        lifecycleScope.launch {
                            authRepo.signOut()
                            context?.let { ctx ->
                                startActivity(
                                    Intent(ctx, AuthActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                )
                            }
                        }
                    }
                ).show(requireActivity().supportFragmentManager, ProfileOptionsBottomSheet.TAG)
            }
        }
    }
    
    private fun setupMyProfileUI() {
        // Hide Back button for own profile (Main Tab)
        binding.btnBack.visibility = View.GONE
        binding.btnMenu.visibility = View.VISIBLE

        // Show Edit/Share buttons
        binding.btnEditProfile.text = "Edit profil"
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        binding.btnShareProfile.text = "Bagikan profil"
        binding.btnShareProfile.setOnClickListener {
            val username = profileVM.user.value?.username ?: "user"
            val shareText = "Check out @$username's profile!"
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Profil"))
        }

        // Chip logic handled in bindProfileText
        binding.chipAddBio.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    private fun setupPublicProfileUI(targetUserId: String) {
        // Show Back button for other profiles
        binding.btnBack.visibility = View.VISIBLE
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // Hide/Config Menu if needed (currently maintaining visibility)
        binding.btnMenu.visibility = View.VISIBLE 

        // Repurpose Edit Profile -> Follow Button
        binding.btnEditProfile.text = "Ikuti" // Default, will change on observe
        binding.btnEditProfile.setOnClickListener {
            profileVM.toggleFollow(targetUserId)
        }

        // Repurpose Share Profile -> Message Button
        binding.btnShareProfile.text = "Pesan"
        binding.btnShareProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur pesan segera hadir!", Toast.LENGTH_SHORT).show()
        }
        
        // Hide "Add Bio" chip explicitly
        binding.chipAddBio.visibility = View.GONE
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        if (isFollowing) {
            binding.btnEditProfile.text = "Mengikuti"
            // Optional: Change style to show outlined/secondary style
             binding.btnEditProfile.setBackgroundColor(Color.TRANSPARENT)
             binding.btnEditProfile.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
             binding.btnEditProfile.strokeWidth = (1 * resources.displayMetrics.density).toInt()
             binding.btnEditProfile.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.border_subtle)
        } else {
            binding.btnEditProfile.text = "Ikuti"
            // Reset to solid style (assuming default is solid black)
             binding.btnEditProfile.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
             binding.btnEditProfile.setTextColor(ContextCompat.getColor(requireContext(), R.color.background))
             binding.btnEditProfile.strokeWidth = 0
        }
    }

    private fun setupViewPager(userId: String?) {
        val currentUser = authRepo.getCurrentUser()
        val currentUserId = currentUser?.id
        
        postsAdapter = com.frzterr.app.ui.home.PostAdapter(
            currentUserId = currentUserId,
            contextType = "profile", // Independent "profile" state
            onLikeClick = { postWithUser ->
                profileVM.toggleLike(postWithUser.post.id, postWithUser.isLiked)
            },
            onCommentClick = { postWithUser ->
                val commentsBottomSheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    postOwnerId = postWithUser.post.userId,
                    onCommentAdded = {
                        val currentUserId = authRepo.getCurrentUser()?.id
                        val currentViewingId = userId
                        profileVM.user.value?.id?.let { uid ->
                            if (uid == currentViewingId) {
                                profileVM.loadUserPosts(uid)
                            }
                        }
                    },
                    onUserClick = { postUserId, avatarUrl ->
                        val bundle = Bundle().apply { 
                            putString("userId", postUserId)
                            putString("avatarUrl", avatarUrl)
                        }

                        if (postUserId == currentUserId) {
                            val navOptions = androidx.navigation.NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setRestoreState(true)
                                .setPopUpTo(findNavController().graph.startDestinationId, false, true)
                                .build()
                            findNavController().navigate(R.id.profileFragment, bundle, navOptions)
                        } else if (postUserId != userId) {
                            findNavController().navigate(R.id.publicProfileFragment, bundle)
                        }
                    }
                )
                commentsBottomSheet.show(
                    requireActivity().supportFragmentManager,
                    com.frzterr.app.ui.comments.CommentsBottomSheet.TAG
                )
            },
            onRepostClick = { postWithUser ->
                profileVM.toggleRepost(postWithUser.post.id, postWithUser.isReposted)
            },
            onUserClick = { postWithUser ->
                 // Check if clicking on same profile
                 if (postWithUser.user.id != currentUserId) {
                     val bundle = Bundle().apply { putString("userId", postWithUser.user.id) }
                     findNavController().navigate(R.id.publicProfileFragment, bundle)
                 } else {
                     // If clicking self while browsing another's profile, go to main profile tab
                     if (userId != currentUserId) {
                        val navOptions = androidx.navigation.NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setRestoreState(true)
                            .setPopUpTo(findNavController().graph.startDestinationId, false, true)
                            .build()
                        findNavController().navigate(R.id.profileFragment, null, navOptions)
                     }
                 }
            },
            onOptionClick = { postWithUser ->
                val isOwner = currentUserId == postWithUser.post.userId
                
                com.frzterr.app.ui.home.PostOptionsBottomSheet(
                    isOwner = isOwner,
                    onCopyClick = {
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Post Content", postWithUser.post.content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Teks disalin", Toast.LENGTH_SHORT).show()
                    },
                    onEditClick = {
                        com.frzterr.app.ui.home.EditPostBottomSheet(
                            post = postWithUser.post,
                            onSaveClick = { newContent ->
                                profileVM.editPost(postWithUser.post, newContent)
                            }
                        ).show(requireActivity().supportFragmentManager, com.frzterr.app.ui.home.EditPostBottomSheet.TAG)
                    },
                    onDeleteClick = {
                        profileVM.deletePost(postWithUser.post)
                    },
                    onNotInterestedClick = {
                        profileVM.hidePost(postWithUser.post.id)
                        Toast.makeText(requireContext(), "Postingan disembunyikan", Toast.LENGTH_SHORT).show()
                    }
                ).show(requireActivity().supportFragmentManager, com.frzterr.app.ui.home.PostOptionsBottomSheet.TAG)
            },
            onImageClick = { images, position, view ->
                // CLICK GUARD (Prevent double open)
                if (System.currentTimeMillis() - profileVM.lastImageClickTime >= 500) {
                    profileVM.lastImageClickTime = System.currentTimeMillis()

                    val bundle = Bundle().apply {
                        putStringArrayList("arg_images", ArrayList(images))
                        putInt("arg_position", position)
                    }
                    
                    val extras = androidx.navigation.fragment.FragmentNavigatorExtras(
                        view to (androidx.core.view.ViewCompat.getTransitionName(view) ?: "")
                    )
                    
                    try {
                        findNavController().navigate(R.id.imageViewerFragment, bundle, null, extras)
                    } catch (e: Exception) {
                        // Fallback without extras if needed
                        findNavController().navigate(R.id.imageViewerFragment, bundle)
                    }
                }
            }
        )

        repostsAdapter = com.frzterr.app.ui.home.PostAdapter(
            currentUserId = currentUserId,
            contextType = "profile", // Independent "profile" state
            onLikeClick = { postWithUser ->
                profileVM.toggleLike(postWithUser.post.id, postWithUser.isLiked)
            },
            onCommentClick = { postWithUser ->
                val commentsBottomSheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    postOwnerId = postWithUser.post.userId,
                    onCommentAdded = {
                        val currentViewingId = userId
                        profileVM.user.value?.id?.let { uid ->
                           if (uid == currentViewingId) {
                                profileVM.loadUserReposts(uid)
                           }
                        }
                    },
                    onUserClick = { postUserId, avatarUrl ->
                        val bundle = Bundle().apply { 
                            putString("userId", postUserId)
                            putString("avatarUrl", avatarUrl)
                        }

                        if (postUserId == currentUserId) {
                            val navOptions = androidx.navigation.NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setRestoreState(true)
                                .setPopUpTo(findNavController().graph.startDestinationId, false, true)
                                .build()
                            findNavController().navigate(R.id.profileFragment, bundle, navOptions)
                        } else if (postUserId != userId) {
                            findNavController().navigate(R.id.publicProfileFragment, bundle)
                        }
                    }
                )
                commentsBottomSheet.show(
                    requireActivity().supportFragmentManager,
                    com.frzterr.app.ui.comments.CommentsBottomSheet.TAG
                )
            },
            onRepostClick = { postWithUser ->
                profileVM.toggleRepost(postWithUser.post.id, postWithUser.isReposted)
            },
            onUserClick = { postWithUser ->
                 // Check if clicking on same profile
                 if (postWithUser.user.id != currentUserId) {
                     val bundle = Bundle().apply { putString("userId", postWithUser.user.id) }
                     findNavController().navigate(R.id.publicProfileFragment, bundle)
                 } else {
                     // If clicking self while browsing another's profile, go to main profile tab
                     if (userId != currentUserId) {
                        val navOptions = androidx.navigation.NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setRestoreState(true)
                            .setPopUpTo(findNavController().graph.startDestinationId, false, true)
                            .build()
                        findNavController().navigate(R.id.profileFragment, null, navOptions)
                     }
                 }
            },
            onOptionClick = { postWithUser ->
                val isOwner = currentUserId == postWithUser.post.userId
                
                com.frzterr.app.ui.home.PostOptionsBottomSheet(
                    isOwner = isOwner,
                    onCopyClick = {
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Post Content", postWithUser.post.content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Teks disalin", Toast.LENGTH_SHORT).show()
                    },
                    onEditClick = {
                        com.frzterr.app.ui.home.EditPostBottomSheet(
                            post = postWithUser.post,
                            onSaveClick = { newContent ->
                                profileVM.editPost(postWithUser.post, newContent)
                            }
                        ).show(requireActivity().supportFragmentManager, com.frzterr.app.ui.home.EditPostBottomSheet.TAG)
                    },
                    onDeleteClick = {
                        profileVM.deletePost(postWithUser.post)
                    },
                    onNotInterestedClick = {
                        profileVM.hidePost(postWithUser.post.id)
                        Toast.makeText(requireContext(), "Postingan disembunyikan", Toast.LENGTH_SHORT).show()
                    }
                ).show(requireActivity().supportFragmentManager, com.frzterr.app.ui.home.PostOptionsBottomSheet.TAG)
            },
            onImageClick = { images, position, view ->
                // CLICK GUARD (Prevent double open)
                if (System.currentTimeMillis() - profileVM.lastImageClickTime >= 500) {
                    profileVM.lastImageClickTime = System.currentTimeMillis()

                    val bundle = Bundle().apply {
                        putStringArrayList("arg_images", ArrayList(images))
                        putInt("arg_position", position)
                    }
                    
                    val extras = androidx.navigation.fragment.FragmentNavigatorExtras(
                        view to (androidx.core.view.ViewCompat.getTransitionName(view) ?: "")
                    )
                    
                    try {
                        findNavController().navigate(R.id.imageViewerFragment, bundle, null, extras)
                    } catch (e: Exception) {
                        findNavController().navigate(R.id.imageViewerFragment, bundle)
                    }
                }
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

        // Observe user posts - HANDLE LOADING via NULL check
        profileVM.userPosts.observe(viewLifecycleOwner) { posts ->
            val postsFragment = childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment
            
            if (posts == null) {
                // LOADING STATE
                binding.viewPager.post {
                    (childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment)?.showLoading(true)
                }
            } else {
                // DATA LOADED
                postsAdapter.submitList(posts) {
                    // Start transition once current tab is laid out
                    if (binding.viewPager.currentItem == 0) startPostponedEnterTransition()
                }
                binding.viewPager.post {
                    val fragment = childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment
                    fragment?.showLoading(false)
                    fragment?.updateEmptyState(posts.isEmpty())
                }
            }
        }

        // Observe user reposts - HANDLE LOADING via NULL check
        profileVM.userReposts.observe(viewLifecycleOwner) { reposts ->
            val repostsFragment = childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment
            
            if (reposts == null) {
                // LOADING STATE
                binding.viewPager.post {
                    (childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment)?.showLoading(true)
                }
            } else {
                // DATA LOADED
                repostsAdapter.submitList(reposts) {
                    // Start transition once current tab is laid out
                    if (binding.viewPager.currentItem == 1) startPostponedEnterTransition()
                }
                binding.viewPager.post {
                    val fragment = childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment
                    fragment?.showLoading(false)
                    fragment?.updateEmptyState(reposts.isEmpty())
                }
            }
        }

        // 🔥 Set adapters IMMEDIATELY (Removing postDelayed to fix stutter)
        // 🔥 Set adapters IMMEDIATELY (Removing postDelayed to fix stutter)
        binding.viewPager.post {
            val postsFragment = childFragmentManager.findFragmentByTag("f0") as? ProfileTabFragment
            val repostsFragment = childFragmentManager.findFragmentByTag("f1") as? ProfileTabFragment
            
            postsFragment?.setAdapter(postsAdapter)
            repostsFragment?.setAdapter(repostsAdapter)
            
            // Trigger initial update from cache
            val posts = profileVM.userPosts.value
            val reposts = profileVM.userReposts.value
            
            if (posts != null) {
                 postsFragment?.showLoading(false)
                 postsFragment?.updateEmptyState(posts.isEmpty())
            } else {
                 postsFragment?.showLoading(true)
            }
            
            if (reposts != null) {
                 repostsFragment?.showLoading(false)
                 repostsFragment?.updateEmptyState(reposts.isEmpty())
            } else {
                 repostsFragment?.showLoading(true)
            }
            
            // 🚀 START TRANSITION ASAP!
            // Don't wait for network data. Show the UI structure (and shimmers) immediately via transition.
            // A short delay ensures the ViewPager is attached and measured.
            binding.root.postDelayed({
                startPostponedEnterTransition()
            }, 100)
        }
    }

    private fun loadProfile(
        userId: String? = null,
        force: Boolean = false,
        showLoading: Boolean = false
    ) {
        val targetId = userId ?: profileVM.user.value?.id
        if (!force && targetId != null && profileVM.user.value?.id == targetId) return

        lifecycleScope.launch {
            try {
                // Note: If userId is explicit, we fetch that. If not, we fallback to current auth user.
                val idToFetch = if (userId != null) {
                    userId
                } else {
                    authRepo.getCurrentUser()?.id
                } ?: return@launch
                
                val dbUser = userRepo.getUserByIdForce(idToFetch) ?: return@launch

                // Update Shared ViewModel (Triggers observer in Fragment)
                profileVM.updateUser(dbUser)
                
                // Only save to local store if it is MY profile
                val currentUser = authRepo.getCurrentUser()
                if (currentUser != null && currentUser.id == idToFetch) {
                    ProfileLocalStore.save(
                        requireContext(),
                        dbUser.fullName,
                        dbUser.avatarUrl,
                        dbUser.username
                    )
                }

            } catch (e: Exception) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Variabel untuk track apakah gambar lokal sukses di-load
    private var isLocalAvatarLoaded = false
    private var localSavedAvatarUrl: String? = null

    // ================= LOAD AVATAR FROM LOCAL FILE =================
    private fun loadLocalAvatarSync() {
        // 1. FAST PATH: Check Memory Cache (MUST be on Main Thread)
        // Load metadata dulu untuk dapat URL yang tersimpan
        val (name, avatarUrl, username) = ProfileLocalStore.load(requireContext())
        localSavedAvatarUrl = avatarUrl

        if (avatarUrl != null) {
            val imageLoader = requireContext().imageLoader
            // Try memory cache first (Instant)
            val memoryCacheKey = coil.memory.MemoryCache.Key(avatarUrl)
            val memoryBitmap = imageLoader.memoryCache?.get(memoryCacheKey)
            
            if (memoryBitmap != null) {
                binding.imgAvatar.setImageBitmap(memoryBitmap.bitmap)
                binding.imgAvatar.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                isLocalAvatarLoaded = true
                return
            }
        }

        // 2. SLOW PATH: Disk Access (Offload to IO)
        lifecycleScope.launch(Dispatchers.IO) {
            // Check Coil Disk Cache
            var foundBitmap: Bitmap? = null
            
            if (avatarUrl != null) {
                 // Note: accessing disk cache APIs might be experimental, handle with care or skip if strict
                 // simplified logic: if not in memory, let standard coil load handle it or check file path
            }

            // Fallback: Check Local File Path (Custom implementation)
            val localAvatarPath = ProfileLocalStore.loadLocalAvatarPath(requireContext())
            if (localAvatarPath != null) {
                val file = File(localAvatarPath)
                if (file.exists()) {
                    // Load bitmap from file
                     try {
                        // Decode simplisticly for preview
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        foundBitmap = bitmap
                     } catch (e: Exception) {
                         e.printStackTrace()
                     }
                }
            }
            
            // 3. Update UI on Main Thread
            withContext(Dispatchers.Main) {
                if (foundBitmap != null) {
                    binding.imgAvatar.setImageBitmap(foundBitmap)
                    binding.imgAvatar.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                    isLocalAvatarLoaded = true
                } else {
                     // Final Fallback: Placeholder (only if not already carrying an image)
                     if (binding.imgAvatar.drawable == null) {
                        binding.imgAvatar.setImageResource(R.drawable.ic_user_placeholder)
                        binding.imgAvatar.background =
                            ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                     }
                     isLocalAvatarLoaded = false
                }
            }
        }
    }
    
    // ================= UPDATE AVATAR IF URL CHANGED =================
    private fun updateAvatarIfNeeded(newAvatarUrl: String?) {
        // 🔥 ANTI-KEDIP LOGIC v5 - Use current image as placeholder!
        val hasVersion = newAvatarUrl?.contains("?v=") ?: false
        
        // Skip if same URL and already displayed (unless versioned update)
        if (!hasVersion && newAvatarUrl == profileVM.lastRenderedAvatarUrl && binding.imgAvatar.drawable != null) {
            return
        }

        // Only show shimmer if no current image (first load)
        val shouldShowShimmer = binding.imgAvatar.drawable == null
        
        if (shouldShowShimmer) {
            binding.shimmerAvatar.visibility = View.VISIBLE
            binding.shimmerAvatar.startShimmer()
        }
        
        // Update rendered state
        profileVM.lastRenderedAvatarUrl = newAvatarUrl
        
        // Load with Coil (it handles caching automatically)
        binding.imgAvatar.load(newAvatarUrl) {
            crossfade(false)
            memoryCacheKey(newAvatarUrl)
            diskCacheKey(newAvatarUrl)
            size(512)

            // Use current image as placeholder (prevents gray flash)
            placeholder(binding.imgAvatar.drawable)
            
            if (newAvatarUrl == null) {
                error(R.drawable.ic_user_placeholder)
                fallback(R.drawable.ic_user_placeholder)
            } else {
                error(R.drawable.ic_user_placeholder)
            }
            
            listener(
                onSuccess = { _, _ ->
                    binding.shimmerAvatar.stopShimmer()
                    binding.shimmerAvatar.visibility = View.GONE
                },
                onError = { _, _ ->
                    binding.shimmerAvatar.stopShimmer()
                    binding.shimmerAvatar.visibility = View.GONE
                }
            )
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

        // Bio - show text or chip
        if (!user.bio.isNullOrBlank()) {
            binding.tvBio.text = user.bio
            binding.tvBio.visibility = View.VISIBLE
            binding.chipAddBio.visibility = View.GONE
        } else {
            binding.tvBio.visibility = View.GONE
            // Only show "Add Bio" if it matches current auth user
            val currentUserId = runBlocking { authRepo.getCurrentUser()?.id }
            if (user.id == currentUserId) {
                 binding.chipAddBio.visibility = View.VISIBLE
            } else {
                 binding.chipAddBio.visibility = View.GONE
            }
        }
    }

    // ================= BIND UI (LEGACY - WITH AVATAR) =================
    private fun bindProfile(user: AppUser) {
        bindProfileText(user)
        
        binding.imgAvatar.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)

        // Check if we already rendered this URL instantly
        if (user.avatarUrl == profileVM.lastRenderedAvatarUrl && user.avatarUrl != null) {
            // SKIP loading to prevent flickering
            binding.shimmerAvatar.stopShimmer()
            binding.shimmerAvatar.visibility = View.GONE
        } else {
             binding.shimmerAvatar.visibility = View.VISIBLE
             binding.shimmerAvatar.startShimmer()
             
             binding.imgAvatar.load(user.avatarUrl) {
                crossfade(false)
                size(256)
                listener(
                    onSuccess = { _, _ ->
                        binding.shimmerAvatar.stopShimmer()
                        binding.shimmerAvatar.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        binding.shimmerAvatar.stopShimmer()
                        binding.shimmerAvatar.visibility = View.GONE
                    }
                )
                
                if (user.avatarUrl == null) {
                    error(R.drawable.ic_user_placeholder)
                } else {
                    error(null)
                }
            }
            profileVM.lastRenderedAvatarUrl = user.avatarUrl
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

                val updatedUser = profileVM.user.value?.copy(avatarUrl = versionedUrl)
                profileVM.updateUser(updatedUser)

                ProfileLocalStore.save(
                    requireContext(),
                    updatedUser?.fullName,
                    versionedUrl,
                    updatedUser?.username
                )

            } catch (e: Exception) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Gagal upload avatar", Toast.LENGTH_SHORT).show()
                }
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
                0.299 * android.graphics.Color.red(color) +
                0.587 * android.graphics.Color.green(color) +
                0.114 * android.graphics.Color.blue(color)
                ) / 255
        return darkness >= 0.5
    }

    // ================= HELPER: FORMAT COUNT =================
    private fun formatCount(count: Long): String {
        return when {
            count < 10000 -> {
                // Format: 1.000 (ribuan pake titik)
                String.format(java.util.Locale.GERMANY, "%,d", count)
            }
            count < 1000000 -> {
                // Format: 10K, 100K (tanpa desimal)
                val k = count / 1000
                "${k}K"
            }
            else -> {
                // Format: 1JT, 1,1JT (max 1 desimal, pake koma)
                val millions = count / 1000000.0
                val formatted = String.format("%.1f", millions).replace('.', ',')
                // Remove ",0" if exact million
                val finalString = if (formatted.endsWith(",0")) {
                     formatted.substring(0, formatted.length - 2)
                } else {
                     formatted
                }
                "${finalString}JT"
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun restoreAppBarOffset() {
        if (profileVM.appBarOffset < 0) {
            isRestoringOffset = true
            
            // 1. Force collapsed state IMMEDIATELY (prevents "Open" flash)
            binding.appBarLayout.setExpanded(false, false)
            
            // 2. Apply exact offset via Behavior in post (as soon as behavior is attached)
            binding.appBarLayout.post {
                try {
                    val params = binding.appBarLayout.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                    val behavior = params?.behavior as? AppBarLayout.Behavior
                    if (behavior != null) {
                        behavior.topAndBottomOffset = profileVM.appBarOffset
                        binding.appBarLayout.requestLayout()
                    }
                } catch (e: Exception) {
                    // Fallback already handled by setExpanded
                }
                // Reset flag
                isRestoringOffset = false
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Restore on start juga (fallback)
        restoreAppBarOffset()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Restore on resume (untuk tab switching)
        restoreAppBarOffset()
        
        // 🔄 Reload posts/reposts when returning to own profile
        // This ensures new posts created in CreatePostActivity appear immediately
        lifecycleScope.launch {
            val currentUserId = authRepo.getCurrentUser()?.id
            val viewingUserId = profileVM.user.value?.id
            
            // Only refresh if viewing own profile
            if (currentUserId != null && currentUserId == viewingUserId) {
                // 🆕 Reload profile data to detect avatar changes from other devices
                loadProfile(currentUserId, force = true)
                
                // Reload posts/reposts
                profileVM.loadUserPosts(currentUserId, force = true)
                profileVM.loadUserReposts(currentUserId, force = true)
            }
        }
    }
}