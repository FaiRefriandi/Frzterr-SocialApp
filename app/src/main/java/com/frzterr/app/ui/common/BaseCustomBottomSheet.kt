package com.frzterr.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.frzterr.app.R
import com.google.android.material.bottomsheet.BottomSheetBehavior

abstract class BaseCustomBottomSheet : Fragment() {

    abstract fun getLayoutResId(): Int
    abstract fun onSheetCreated(view: View)

    protected var behavior: BottomSheetBehavior<FrameLayout>? = null
    private var scrim: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the wrapper (Coordinator + Scrim + SheetContainer)
        val wrapper = inflater.inflate(R.layout.fragment_custom_bottom_sheet_container, container, false)
        val sheetContainer = wrapper.findViewById<FrameLayout>(R.id.sheetContainer)
        scrim = wrapper.findViewById(R.id.scrim)

        // Inflate the actual content
        inflater.inflate(getLayoutResId(), sheetContainer, true)

        return wrapper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sheetContainer = view.findViewById<FrameLayout>(R.id.sheetContainer)
        
        // Setup Edge-to-Edge: Apply padding to the CHILD content, not the container.
        // This allows the sheet background to extend behind the navigation bar.
        view.post {
            if (sheetContainer.childCount > 0) {
                val content = sheetContainer.getChildAt(0)
                ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                    val extraPadding = (48 * v.context.resources.displayMetrics.density).toInt()
                    v.updatePadding(bottom = bars.bottom + extraPadding)
                    insets
                }
            }
        }

        // Initialize Behavior
        behavior = BottomSheetBehavior.from(sheetContainer)
        behavior?.state = BottomSheetBehavior.STATE_HIDDEN
        behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dismissActual()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Fade scrim based on slide offset (0 to 1)
                // Offset -1 is hidden, 0 is collapsed, 1 is expanded
                // We want 0 alpha at hidden (-1), 1 alpha at expanded (0+?)
                // Actually standard behavior: 
                // offset > 0 (Expanded -> Collapsed? No).
                // Just simple map: if offset >= -1.
                // Simplified: Scrim visible if not hidden.
                // Better: 
                val alpha = if (slideOffset >= 0) 1f else (1f + slideOffset)
                scrim?.alpha = alpha
            }
        })

        // Dismiss on Scrim Click
        scrim?.setOnClickListener { dismiss() }

        // Handle Back Press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        })

        // Delegate to child
        onSheetCreated(view)

        // Expand after layout
        view.post {
            scrim?.animate()?.alpha(1f)?.setDuration(200)?.start()
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun dismiss() {
        // Animate out
        scrim?.animate()?.alpha(0f)?.setDuration(200)?.start()
        behavior?.state = BottomSheetBehavior.STATE_HIDDEN
    }
    
    // Actually remove fragment
    private fun dismissActual() {
        parentFragmentManager.beginTransaction()
            .remove(this)
            .commitAllowingStateLoss()
    }

    fun show(manager: FragmentManager, tag: String) {
        manager.beginTransaction()
            .add(android.R.id.content, this, tag)
            .commit()
    }
}
