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
    private var scrollView: androidx.core.widget.NestedScrollView? = null

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
        scrollView = view.findViewById(R.id.scrollView)

        // Set empty message based on tab type
        view.findViewById<TextView>(R.id.tvEmptyMessage).text = when (tabType) {
            TabType.POSTS -> "Belum ada postingan"
            TabType.REPOSTS -> "Belum ada repost"
        }
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        recyclerView?.adapter = adapter
    }

    fun updateEmptyState(isEmpty: Boolean) {
        emptyState?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        
        // Disable scroll when empty
        scrollView?.isNestedScrollingEnabled = !isEmpty
        
        // Block touch events when empty
        scrollView?.setOnTouchListener { _, _ -> isEmpty }
    }

    fun getTabType(): TabType = tabType
}
