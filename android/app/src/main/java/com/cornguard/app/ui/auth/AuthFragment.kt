package com.cornguard.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentAuthBinding
import kotlinx.coroutines.launch

/**
 * Sign-in/registration, reached only from an online-only screen's "sign in required" prompt
 * (e.g. Community) — never from app launch. See [AuthViewModel] and
 * firebase/repositories/auth-repository-interface.md /
 * user-farm-repository-interface.md for the two-step registration this drives.
 */
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.authToggleModeButton.setOnClickListener {
            val newMode = if (viewModel.uiState.value.mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN
            viewModel.setMode(newMode)
        }

        binding.authSkipButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.authSubmitButton.setOnClickListener {
            when (viewModel.uiState.value.mode) {
                AuthMode.SIGN_IN -> viewModel.signIn(
                    binding.emailInput.text.toString().trim(),
                    binding.passwordInput.text.toString()
                )
                AuthMode.REGISTER -> viewModel.register(
                    displayName = binding.displayNameInput.text.toString().trim(),
                    email = binding.emailInput.text.toString().trim(),
                    password = binding.passwordInput.text.toString(),
                    barangay = binding.barangayInput.text.toString().trim(),
                    municipality = binding.municipalityInput.text.toString().trim(),
                    province = binding.provinceInput.text.toString().trim()
                )
            }
        }

        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: AuthUiState) {
        val isRegister = state.mode == AuthMode.REGISTER
        binding.authTitle.setText(if (isRegister) R.string.auth_title_register else R.string.auth_title_signin)
        binding.authSubmitButton.setText(if (isRegister) R.string.auth_register_action else R.string.auth_signin_action)
        binding.authToggleModeButton.setText(
            if (isRegister) R.string.auth_toggle_to_signin else R.string.auth_toggle_to_register
        )

        val registerFieldsVisibility = if (isRegister) View.VISIBLE else View.GONE
        binding.displayNameInput.visibility = registerFieldsVisibility
        binding.barangayInput.visibility = registerFieldsVisibility
        binding.municipalityInput.visibility = registerFieldsVisibility
        binding.provinceInput.visibility = registerFieldsVisibility

        binding.authProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.authSubmitButton.isEnabled = !state.isLoading

        if (state.errorMessage != null) {
            binding.authError.visibility = View.VISIBLE
            binding.authError.text = if (state.errorMessage == AuthViewModel.MISSING_FIELDS) {
                getString(R.string.auth_error_missing_fields)
            } else {
                state.errorMessage
            }
        } else {
            binding.authError.visibility = View.GONE
        }

        if (state.completed) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
