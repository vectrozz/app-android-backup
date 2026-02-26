package com.zkbackup.app.ui.setup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.zkbackup.app.databinding.ActivitySetupBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Onboarding screen: enter server URL, register/login, set master encryption password.
 */
@AndroidEntryPoint
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val masterPw = binding.etMasterPassword.text.toString()
            val selfHosted = binding.switchSelfHosted.isChecked

            if (!validate(url, email, password, masterPw)) return@setOnClickListener

            viewModel.register(url, email, password, masterPw, selfHosted)
        }

        binding.btnLogin.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val masterPw = binding.etMasterPassword.text.toString()
            val selfHosted = binding.switchSelfHosted.isChecked

            if (!validate(url, email, password, masterPw)) return@setOnClickListener

            viewModel.login(url, email, password, masterPw, selfHosted)
        }

        binding.btnSave.setOnClickListener {
            viewModel.completeSetup()
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.tvSetupStatus.isVisible = state.message != null
                binding.tvSetupStatus.text = state.message

                binding.btnSave.isEnabled = state.isAuthenticated

                if (state.setupComplete) {
                    Toast.makeText(this@SetupActivity, "Setup complete!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun validate(url: String, email: String, password: String, masterPw: String): Boolean {
        if (url.isEmpty() || !url.startsWith("http")) {
            binding.tvSetupStatus.isVisible = true
            binding.tvSetupStatus.text = "Enter a valid server URL (https://...)"
            return false
        }
        if (email.isEmpty() || !email.contains("@")) {
            binding.tvSetupStatus.isVisible = true
            binding.tvSetupStatus.text = "Enter a valid email"
            return false
        }
        if (password.length < 8) {
            binding.tvSetupStatus.isVisible = true
            binding.tvSetupStatus.text = "Password must be at least 8 characters"
            return false
        }
        if (masterPw.length < 8) {
            binding.tvSetupStatus.isVisible = true
            binding.tvSetupStatus.text = "Master password must be at least 8 characters"
            return false
        }
        return true
    }
}
