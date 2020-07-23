/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tv.reference.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.tv.reference.R
import com.google.android.gms.auth.api.identity.SignInCredential

/**
 * Handles sign in and sign out process and exposes methods used to authenticate the user and
 * persist and retrieve user information.
 */
class UserManager(
    private val server: AuthClient,
    private val storage: UserInfoStorage
) {
    private val userInfoLiveData = MutableLiveData(storage.readUserInfo())
    val userInfo: LiveData<UserInfo?> = userInfoLiveData

    init {
        validateToken()
    }

    fun isSignedIn() = userInfoLiveData.value != null

    fun signOut() {
        userInfoLiveData.value?.let {
            // TODO: log server error
            server.invalidateToken(it.token)
            clearUserInfo()
        }
    }

    fun authWithPassword(username: String, password: String): AuthResult {
        return when (val result = server.authWithPassword(username, password)) {
            is AuthClientResult.Success -> {
                updateUserInfo(result.value)
                AuthResult.Success
            }
            is AuthClientResult.Failure -> AuthResult.Failure(result.error)
        }
    }

    fun authWithGoogle(credential: SignInCredential): AuthResult {
        TODO("Not yet implemented")
    }

    private fun validateToken() {
        userInfoLiveData.value?.let {
            val result = server.validateToken(it.token)
            if (result is AuthClientResult.Success) {
                updateUserInfo(result.value)
            } else {
                clearUserInfo()
            }
        }
    }

    private fun updateUserInfo(userInfo: UserInfo) {
        userInfoLiveData.postValue(userInfo)
        storage.writeUserInfo(userInfo)
    }

    private fun clearUserInfo() {
        userInfoLiveData.postValue(null)
        storage.clearUserInfo()
    }

    sealed class AuthResult {
        object Success : AuthResult()
        data class Failure(val error: AuthClientError) : AuthResult()
    }

    companion object {
        const val signInFragmentId = R.id.action_global_signInFragment

        @Volatile
        private var INSTANCE: UserManager? = null

        fun getInstance(context: Context): UserManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDefault(context).also { INSTANCE = it }
            }

        private fun createDefault(context: Context) =
            UserManager(MockAuthClient(), DefaultUserInfoStorage(context))
    }
}
