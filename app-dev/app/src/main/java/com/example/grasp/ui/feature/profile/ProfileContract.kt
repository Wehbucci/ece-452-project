package com.example.grasp.ui.feature.profile

import com.example.grasp.core.mvp.MvpPresenter
import com.example.grasp.core.mvp.MvpView

interface ProfileContract {

    interface View : MvpView {
        fun showProfile(name: String, email: String)
        fun onLoggedOut()
    }

    interface Presenter : MvpPresenter<View> {
        fun onLogout()
    }
}
