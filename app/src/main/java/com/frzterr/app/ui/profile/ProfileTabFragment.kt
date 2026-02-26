package com.frzterr.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R

class ProfileTabFragment : androidx.fragment.app.Fragment() {

    enum class TabType {
        POSTS, REPOSTS
    }

    private var tabType: TabType = TabType.POSTS
    private var recyclerView: RecyclerView? = null
    private var emptyState: LinearLayout? = null
    private var shimmerViewContainer: com.facebook.shimmer.ShimmerFrameLayout? = null

    companion object {
        private const val ARG_TAB_TYPE = "tab_type"

        fun newInstance(tabType: TabType): ProfileTabFragment {
            return ProfileTabFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TAB_TYPE, tabType.ordinal)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabType = TabType.values()[it.getInt(ARG_TAB_TYPE, 0)]
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.rvTabContent)
        emptyState = view.findViewById(R.id.emptyState)
        shimmerViewContainer = view.findViewById(R.id.shimmerViewContainer)

        // Set empty message based on tab type
        view.findViewById<TextView>(R.id.tvEmptyMessage).text = when (tabType) {
            TabType.POSTS -> "Belum ada postingan"
            TabType.REPOSTS -> "Belum ada repost"
        }

        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav) ?: return
                
                // Threads-like synchronous scroll
                val currentTranslation = bottomNav.translationY
                val newTranslation = (currentTranslation + dy).coerceIn(0f, bottomNav.height.toFloat())
                
                bottomNav.translationY = newTranslation
            }
        })
        
        // 🔥 FIX: Enable horizontal swipe for image carousels within posts
        // This allows the horizontal RecyclerView (image carousel) to capture horizontal swipes
        // while still allowing ViewPager2 (tab switching) to work for large horizontal gestures
        setupRecyclerViewTouchHandling()
    }
    
    private fun setupRecyclerViewTouchHandling() {
        recyclerView?.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var startX = 0f
            private var startY = 0f
            
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        
                        // 🔥 Critical Fix: Aggressively prevent ViewPager2 from stealing event immediately
                        // identifying if we are touching a carousel
                        val childView = rv.findChildViewUnder(e.x, e.y)
                        if (childView != null) {
                            val carousel = childView.findViewById<RecyclerView>(R.id.rvPostImages)
                            if (carousel != null && carousel.visibility == View.VISIBLE) {
                                val rect = android.graphics.Rect()
                                carousel.getGlobalVisibleRect(rect)
                                if (rect.contains(e.rawX.toInt(), e.rawY.toInt())) {
                                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                        }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - startX
                        val dy = e.y - startY
                        val isHorizontal = Math.abs(dx) > Math.abs(dy)
                        
                        if (isHorizontal) {
                            val childView = rv.findChildViewUnder(e.x, e.y)
                            if (childView != null) {
                                val carousel = childView.findViewById<RecyclerView>(R.id.rvPostImages)
                                if (carousel != null && carousel.visibility == View.VISIBLE) {
                                    val rect = android.graphics.Rect()
                                    carousel.getGlobalVisibleRect(rect)
                                    if (rect.contains(e.rawX.toInt(), e.rawY.toInt())) {
                                        // Scroll Direction: Swiping RIGHT (dx > 0) means scrolling BACK (-1)
                                        // Swiping LEFT (dx < 0) means scrolling FORWARD (1)
                                        val direction = if (dx > 0) -1 else 1
                                        
                                        if (carousel.canScrollHorizontally(direction)) {
                                            // Carousel can scroll -> Keep parent LOCKED
                                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                                        } else {
                                            // Carousel at edge -> UNLOCK parent to allow tab switch
                                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        recyclerView?.adapter = adapter
    }

    fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            shimmerViewContainer?.visibility = View.VISIBLE
            shimmerViewContainer?.startShimmer()
            recyclerView?.visibility = View.GONE
            emptyState?.visibility = View.GONE
        } else {
            shimmerViewContainer?.stopShimmer()
            shimmerViewContainer?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    fun updateEmptyState(isEmpty: Boolean) {
        if (shimmerViewContainer?.visibility != View.VISIBLE) {
            emptyState?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
    }
    
    fun getRecyclerView(): RecyclerView? = recyclerView

    fun getTabType(): TabType = tabType
}
