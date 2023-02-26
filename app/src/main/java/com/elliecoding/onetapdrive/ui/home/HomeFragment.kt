package com.elliecoding.onetapdrive.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.elliecoding.onetapdrive.UserViewModel
import com.elliecoding.onetapdrive.databinding.FragmentHomeBinding
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

private const val TAG = "HomeFragment"

class HomeFragment : Fragment() {

    private val userViewModel: UserViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        userViewModel.userLoginStatus.observe(viewLifecycleOwner) { isLoggedIn ->
            binding.textLogin.text = if (isLoggedIn) "Logged in" else "Not logged in"
            binding.inputData.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            binding.inputButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            binding.textDataLabel.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            if (isLoggedIn) {
                try {
                    userViewModel.download(
                        requireActivity() as UserViewModel.UserEventCallback,
                        requireContext()
                    )
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, 9);
                }
            }
        }

        userViewModel.storedData.observe(viewLifecycleOwner) {
            binding.textData.text = it
        }
        binding.inputButton.setOnClickListener {
            storeText(binding.inputData.text.toString())
        }
        return root
    }

    private fun storeText(text: String) {
        binding.textData.text = text
        userViewModel.upload(requireContext(), text)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}