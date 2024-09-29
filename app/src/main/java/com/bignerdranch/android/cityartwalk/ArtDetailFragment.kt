package com.bignerdranch.android.cityartwalk

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bignerdranch.android.cityartwalk.databinding.FragmentArtDetailBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import getScaledBitmap
import java.io.File
import java.util.Date

private const val DATE_FORMAT = "EEE, MMM, dd"

class ArtDetailFragment : Fragment() {

    private var _binding: FragmentArtDetailBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }

    private val args: ArtDetailFragmentArgs by navArgs()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    private val artDetailViewModel: ArtDetailViewModel by viewModels {
        ArtDetailViewModelFactory(args.artId)
    }

    private val selectSuspect = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { parseContactSelection(it) }
    }

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { didTakePhoto: Boolean ->
        if (didTakePhoto && photoName != null) {
            artDetailViewModel.updateArt { oldArt ->
                oldArt.copy(photoFileName = photoName)
            }
        }
    }

    private var photoName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding =
            FragmentArtDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            artTitle.doOnTextChanged { text, _, _, _ ->
                artDetailViewModel.updateArt { oldArt ->
                    oldArt.copy(title = text.toString())
                }
            }

            artAddress.doOnTextChanged { text, _, _, _ ->
                artDetailViewModel.updateArt { oldArt ->
                    oldArt.copy(address = text.toString())
                }
            }

            artSolved.setOnCheckedChangeListener { _, isChecked ->
                artDetailViewModel.updateArt { oldArt ->
                    oldArt.copy(isSolved = isChecked)
                }
            }

            artSuspect.setOnClickListener {
                selectSuspect.launch(null)
            }

            artCamera.setOnClickListener {
                photoName = "IMG_${Date()}.JPG"
                val photoFile = File(requireContext().applicationContext.filesDir, photoName)
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.bignerdranch.android.cityartwalk.fileprovider",
                    photoFile
                )

                takePhoto.launch(photoUri)

            }

            showMap.setOnClickListener {
                artDetailViewModel.art.value?.let { art ->
                    val intent = Intent(requireContext(), MapsActivity::class.java).apply {
                        putExtra("LATITUDE", art.latitude)
                        putExtra("LONGITUDE", art.longitude)
                    }
                    startActivity(intent)
                }
            }

            getGps.setOnClickListener {
                if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("GPS", "Have permissions. Try to get a location")
                if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(requireContext()) == ConnectionResult.SUCCESS
                ) {
                    fusedLocationProviderClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        object : CancellationToken() {
                            override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                                CancellationTokenSource().token

                            override fun isCancellationRequested() = false
                        }).addOnSuccessListener { location: Location? ->
                        location?.let {
                            artDetailViewModel.updateArt { oldArt ->
                                oldArt.copy(latitude = location.latitude, longitude = location.longitude)
                            }
                            // Update the TextView with the new location values
                            val locationText = "Lat: ${location.latitude}, Long: ${location.longitude}"
                            binding.artGps.text = locationText

                        }
                        Log.d("GPS", "Got a location" + location)
                        }
                    }

                } else {Log.d("GPS", "Location permission denied") }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                artDetailViewModel.art.collect { art ->
                    art?.let { updateUi(it) }
                }
            }
        }

        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->
            val newDate =
                bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date
            artDetailViewModel.updateArt { it.copy(date = newDate) }

        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(art: Art) {
        binding.apply {
            if (artTitle.text.toString() != art.title) {
                artTitle.setText(art.title)
            }

            if (artAddress.text.toString() != art.address) {
                artAddress.setText(art.address)
            }

            if (artGps.text.toString() != "Lat: ${art.latitude}, Long: ${art.longitude}") {
                artGps.setText("Lat: ${art.latitude}, Long: ${art.longitude}")
            }

            artDate.text = art.date.toString()
            artDate.setOnClickListener {
                findNavController().navigate(
                    ArtDetailFragmentDirections.selectDate(art.date)
                )
            }
            artSolved.isChecked = art.isSolved

            artReport.setOnClickListener {
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getArtReport(art))
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.art_report_subject)
                    )
                }

                val chooserIntent = Intent.createChooser(
                    reportIntent,
                    getString(R.string.send_report)
                )
                startActivity(chooserIntent)
            }

            artSuspect.text = art.suspect.ifEmpty {
                getString(R.string.art_suspect_text)
            }

            updatePhoto(art.photoFileName)
        }
    }

    private fun getArtReport(art: Art): String {
        val solvedString = if (art.isSolved) {
            getString(R.string.art_report_solved)
        } else {
            getString(R.string.art_report_unsolved)
        }

        val dateString = DateFormat.format(DATE_FORMAT, art.date).toString()
        val suspectText = if (art.suspect.isBlank()) {
            getString(R.string.art_report_no_suspect)
        } else {
            getString(R.string.art_report_suspect, art.suspect)
        }

        return getString(
            R.string.art_report,
            art.title, art.address, dateString, suspectText
        )
    }

    private fun parseContactSelection(contactUri: Uri) {
        val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)

        val queryCursor = requireActivity().contentResolver
            .query(contactUri, queryFields, null, null, null)
        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val suspect = cursor.getString(0)
                artDetailViewModel.updateArt { oldArt ->
                    oldArt.copy(suspect = suspect)
                }
            }
        }
    }

    private fun canResolveIntent(intent: Intent) : Boolean {
        val packageManager: PackageManager = requireActivity().packageManager
        val resolvedActivity: ResolveInfo? =
            packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
        return resolvedActivity != null
    }

    private fun updatePhoto(photoFileName: String?) {
        if (binding.artPhoto.tag != photoFileName) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, it)
            }

            if (photoFile?.exists() == true) {
                binding.artPhoto.doOnLayout { measuredView ->
                    val scaledBitmap = getScaledBitmap(
                        photoFile.path,
                        measuredView.width,
                        measuredView.height
                    )
                    binding.artPhoto.setImageBitmap(scaledBitmap)
                    binding.artPhoto.tag = photoFileName
                }
            } else {
                binding.artPhoto.setImageBitmap(null)
                binding.artPhoto.tag = null
            }
        }
    }
}