// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveHaptics
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveMotion

/**
 * Material 3 Expressive FAB Menu
 * 
 * Official implementation following Google's M3 Expressive guidelines:
 * - 2-6 related actions
 * - Single size compatible with any FAB
 * - Replaces speed dial and stacked small FABs
 * - Contrasting colors for close button and items
 * - Spring animations for open/close
 * - Haptic feedback on interactions
 * 
 * Based on: https://developer.android.com/design/ui/mobile/guides/components/fab-menu
 */
class FabMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var fab: FloatingActionButton? = null
    private var closeButton: ImageButton? = null
    private var menuContainer: LinearLayout? = null
    private var menuItems: MutableList<MenuItem> = mutableListOf()
    private var isOpen = false
    private var onMenuItemClickListener: ((Int) -> Unit)? = null

    data class MenuItem(
        val id: Int,
        val icon: Int,
        val label: String,
        val enabled: Boolean = true
    )

    init {
        inflate()
        setupAnimations()
    }

    private fun inflate() {
        LayoutInflater.from(context).inflate(R.layout.view_fab_menu, this, true)
        
        fab = findViewById(R.id.fab)
        closeButton = findViewById(R.id.close_button)
        menuContainer = findViewById(R.id.menu_container)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        fab?.setOnClickListener {
            if (isOpen) {
                closeMenu()
            } else {
                openMenu()
            }
        }

        closeButton?.setOnClickListener {
            closeMenu()
        }

        // Setup menu item click listeners
        for (i in 0 until menuContainer?.childCount ?: 0) {
            val itemView = menuContainer?.getChildAt(i)
            itemView?.setOnClickListener { view ->
                val menuItem = view.tag as? MenuItem ?: return@setOnClickListener
                if (menuItem.enabled) {
                    onMenuItemClickListener?.invoke(menuItem.id)
                    ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_SUCCESS)
                    closeMenu()
                }
            }
        }
    }

    private fun setupAnimations() {
        // Load spring-based animations
        val openAnim = AnimationUtils.loadAnimation(context, R.anim.fab_menu_open)
        val closeAnim = AnimationUtils.loadAnimation(context, R.anim.fab_menu_close)
        
        openAnim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_LIGHT)
            }
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        
        closeAnim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_LIGHT)
            }
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
    }

    fun openMenu() {
        if (isOpen) return
        
        isOpen = true
        
        // Animate FAB rotation
        fab?.animate()
            .rotation(45f)
            .setDuration(300)
            .setInterpolator(ExpressiveMotion.Easing.EMPHASIZED)
            .start()
        
        // Show menu items with staggered animation
        menuContainer?.isVisible = true
        closeMenuItems()
        
        // Animate menu items in
        for (i in 0 until (menuContainer?.childCount ?: 0)) {
            val itemView = menuContainer?.getChildAt(i)
            itemView?.alpha = 0f
            itemView?.translationY = 100f
            itemView?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(300)
                ?.setInterpolator(ExpressiveMotion.Easing.EMPHASIZED_DECELERERATE)
                ?.setStartDelay(i * 50L)
                ?.start()
        }
        
        // Show close button
        closeButton?.alpha = 0f
        closeButton?.isVisible = true
        closeButton?.animate()
            ?.alpha(1f)
            ?.setDuration(200)
            ?.setStartDelay(100)
            ?.start()
        
        ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_MEDIUM)
    }

    fun closeMenu() {
        if (!isOpen) return
        
        isOpen = false
        
        // Animate FAB rotation back
        fab?.animate()
            .rotation(0f)
            .setDuration(300)
            .setInterpolator(ExpressiveMotion.Easing.EMPHASIZED)
            .start()
        
        // Animate menu items out
        for (i in 0 until (menuContainer?.childCount ?: 0)) {
            val itemView = menuContainer?.getChildAt(i)
            itemView?.animate()
                ?.alpha(0f)
                ?.translationY(100f)
                ?.setDuration(200)
                ?.setInterpolator(ExpressiveMotion.Easing.EMPHASIZED_ACCELERATE)
                ?.setStartDelay(i * 30L)
                ?.withEndAction {
                    if (i == (menuContainer?.childCount ?: 0) - 1) {
                        menuContainer?.isVisible = false
                        openMenuItems()
                    }
                }
                ?.start()
        }
        
        // Hide close button
        closeButton?.animate()
            ?.alpha(0f)
            ?.setDuration(150)
            ?.withEndAction { closeButton?.isVisible = false }
            ?.start()
        
        ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_LIGHT)
    }

    fun toggleMenu() {
        if (isOpen) {
            closeMenu()
        } else {
            openMenu()
        }
    }

    fun setMenuItems(items: List<MenuItem>) {
        menuItems.clear()
        menuItems.addAll(items)
        rebuildMenuItems()
    }

    fun addMenuItem(item: MenuItem) {
        if (menuItems.size >= 6) {
            throw IllegalStateException("FAB Menu supports maximum 6 items")
        }
        menuItems.add(item)
        rebuildMenuItems()
    }

    fun removeMenuItem(itemId: Int) {
        menuItems.removeAll { it.id == itemId }
        rebuildMenuItems()
    }

    fun setOnMenuItemClickListener(listener: (Int) -> Unit) {
        onMenuItemClickListener = listener
    }

    private fun rebuildMenuItems() {
        menuContainer?.removeAllViews()
        
        menuItems.forEach { item ->
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_fab_menu, menuContainer, false)
            
            val icon = itemView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.item_icon)
            val label = itemView.findViewById<TextView>(R.id.item_label)
            
            icon.setImageResource(item.icon)
            label.text = item.label
            itemView.isEnabled = item.enabled
            itemView.alpha = if (item.enabled) 1f else 0.5f
            itemView.tag = item
            
            // Apply M3 Expressive colors
            icon.imageTintList = ContextCompat.getColorStateList(
                context,
                R.color.m3_sys_color_light_on_primary_container
            )
            
            menuContainer?.addView(itemView)
            
            itemView.setOnClickListener { view ->
                val menuItem = view.tag as? MenuItem ?: return@setOnClickListener
                if (menuItem.enabled) {
                    onMenuItemClickListener?.invoke(menuItem.id)
                    ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_SUCCESS)
                    closeMenu()
                }
            }
        }
        
        closeMenuItems()
    }

    private fun closeMenuItems() {
        // Hide actual menu items (they're shown when menu opens)
        for (i in 0 until (menuContainer?.childCount ?: 0)) {
            menuContainer?.getChildAt(i)?.isVisible = true
        }
    }

    private fun openMenuItems() {
        // Reset menu items state
        for (i in 0 until (menuContainer?.childCount ?: 0)) {
            val itemView = menuContainer?.getChildAt(i)
            itemView?.alpha = 1f
            itemView?.translationY = 0f
        }
    }

    fun setFabIcon(iconResId: Int) {
        fab?.setImageResource(iconResId)
    }

    fun setFabContentDescription(description: String) {
        fab?.contentDescription = description
    }

    companion object {
        const val MAX_ITEMS = 6
    }
}
