package com.raman.focustimerkt

import android.animation.Animator

//override only necessary methods by extending this
open class SimpleAnimatorListener: Animator.AnimatorListener {
    override fun onAnimationStart(animation: Animator?) {
    }

    override fun onAnimationEnd(animation: Animator?) {

    }

    override fun onAnimationCancel(animation: Animator?) {

    }

    override fun onAnimationRepeat(animation: Animator?) {
    }
}