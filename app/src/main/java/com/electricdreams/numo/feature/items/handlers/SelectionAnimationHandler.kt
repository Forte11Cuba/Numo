package com.electricdreams.numo.feature.items.handlers

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

/**
 * Handles all animations for ItemSelectionActivity.
 * Includes basket section, checkout button, and quantity animations.
 */
class SelectionAnimationHandler(
    private val basketSection: LinearLayout,
    private val checkoutContainer: CardView
) {
    
    companion object {
        private const val BASKET_FADE_IN_DURATION = 350L
        private const val BASKET_FADE_OUT_DURATION = 250L
        private const val CHECKOUT_SHOW_DURATION = 400L
        private const val CHECKOUT_HIDE_DURATION = 200L
        private const val QUANTITY_BOUNCE_DURATION = 100L
    }

    /**
     * Smooth fade-in animation for basket section appearing.
     * Uses Apple-like spring interpolation for natural motion.
     */
    fun animateBasketSectionIn() {
        basketSection.visibility = View.VISIBLE
        basketSection.alpha = 0f
        basketSection.translationY = 50f
        basketSection.scaleY = 0.95f

        val fadeIn = ObjectAnimator.ofFloat(basketSection, View.ALPHA, 0f, 1f)
        val slideUp = ObjectAnimator.ofFloat(basketSection, View.TRANSLATION_Y, 50f, 0f)
        val scaleUp = ObjectAnimator.ofFloat(basketSection, View.SCALE_Y, 0.95f, 1f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleUp)
            duration = BASKET_FADE_IN_DURATION
            interpolator = OvershootInterpolator(0.8f)
            start()
        }
    }

    /**
     * Smooth fade-out animation for basket section disappearing.
     * Uses decelerate for natural exit motion.
     */
    fun animateBasketSectionOut() {
        val fadeOut = ObjectAnimator.ofFloat(basketSection, View.ALPHA, 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(basketSection, View.TRANSLATION_Y, 0f, 30f)
        val scaleDown = ObjectAnimator.ofFloat(basketSection, View.SCALE_Y, 1f, 0.97f)

        AnimatorSet().apply {
            playTogether(fadeOut, slideDown, scaleDown)
            duration = BASKET_FADE_OUT_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    basketSection.visibility = View.GONE
                    basketSection.translationY = 0f
                    basketSection.scaleY = 1f
                }
            })
            start()
        }
    }

    /**
     * Smooth animation for checkout button appearing/disappearing.
     * Apple-style bounce effect on appear.
     */
    fun animateCheckoutButton(show: Boolean) {
        if (show) {
            animateCheckoutIn()
        } else {
            animateCheckoutOut()
        }
    }

    private fun animateCheckoutIn() {
        checkoutContainer.visibility = View.VISIBLE
        checkoutContainer.alpha = 0f
        checkoutContainer.translationY = 80f
        checkoutContainer.scaleX = 0.9f
        checkoutContainer.scaleY = 0.9f

        val fadeIn = ObjectAnimator.ofFloat(checkoutContainer, View.ALPHA, 0f, 1f)
        val slideUp = ObjectAnimator.ofFloat(checkoutContainer, View.TRANSLATION_Y, 80f, 0f)
        val scaleX = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_X, 0.9f, 1f)
        val scaleY = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_Y, 0.9f, 1f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleX, scaleY)
            duration = CHECKOUT_SHOW_DURATION
            interpolator = OvershootInterpolator(1.0f)
            start()
        }
    }

    private fun animateCheckoutOut() {
        val fadeOut = ObjectAnimator.ofFloat(checkoutContainer, View.ALPHA, 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(checkoutContainer, View.TRANSLATION_Y, 0f, 60f)
        val scaleX = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_X, 1f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_Y, 1f, 0.95f)

        AnimatorSet().apply {
            playTogether(fadeOut, slideDown, scaleX, scaleY)
            duration = CHECKOUT_HIDE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    checkoutContainer.visibility = View.GONE
                    checkoutContainer.translationY = 0f
                    checkoutContainer.scaleX = 1f
                    checkoutContainer.scaleY = 1f
                }
            })
            start()
        }
    }

    /**
     * Animate quantity change with a bounce effect.
     */
    fun animateQuantityChange(quantityView: TextView) {
        val scaleAnim = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(quantityView, "scaleX", 1f, 1.2f).setDuration(QUANTITY_BOUNCE_DURATION),
                ObjectAnimator.ofFloat(quantityView, "scaleX", 1.2f, 1f).setDuration(QUANTITY_BOUNCE_DURATION)
            )
        }
        val scaleAnimY = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(quantityView, "scaleY", 1f, 1.2f).setDuration(QUANTITY_BOUNCE_DURATION),
                ObjectAnimator.ofFloat(quantityView, "scaleY", 1.2f, 1f).setDuration(QUANTITY_BOUNCE_DURATION)
            )
        }
        AnimatorSet().apply {
            playTogether(scaleAnim, scaleAnimY)
            start()
        }
    }

    // ----- Visibility State Helpers -----

    fun isBasketSectionVisible(): Boolean = basketSection.visibility == View.VISIBLE

    fun isCheckoutContainerVisible(): Boolean = checkoutContainer.visibility == View.VISIBLE
}
