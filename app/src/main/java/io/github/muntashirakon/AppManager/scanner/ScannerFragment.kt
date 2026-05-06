// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.collection.ArrayMap
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.scanner.vt.VtAvEngineResult
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText
import io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText
import io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.util.UiUtils
import java.io.File
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.*
import java.util.regex.Pattern

class ScannerFragment : Fragment() {
    private var mAppName: CharSequence? = null
    private var mViewModel: ScannerViewModel? = null
    private var mActivity: ScannerActivity? = null

    private var mVtContainerView: MaterialCardView? = null
    private var mVtTitleView: TextView? = null
    private var mVtDescriptionView: TextView? = null
    private var pithusDescriptionView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireActivity()).get(ScannerViewModel::class.java)
        mActivity = requireActivity() as ScannerActivity
        val cardColor = ColorCodes.getListItemColor1(mActivity!!)
        val classesView: MaterialCardView = view.findViewById(R.id.classes)
        classesView.setCardBackgroundColor(cardColor)
        val trackersView: MaterialCardView = view.findViewById(R.id.tracker)
        trackersView.setCardBackgroundColor(cardColor)
        mVtContainerView = view.findViewById(R.id.vt)
        mVtContainerView!!.setCardBackgroundColor(cardColor)
        mVtTitleView = view.findViewById(R.id.vt_title)
        mVtDescriptionView = view.findViewById(R.id.vt_description)
        val pithusContainerView: MaterialCardView = view.findViewById(R.id.pithus)
        pithusContainerView.setCardBackgroundColor(cardColor)
        pithusDescriptionView = view.findViewById(R.id.pithus_description)
        val libsView: MaterialCardView = view.findViewById(R.id.libs)
        libsView.setCardBackgroundColor(cardColor)
        val apkInfoView: MaterialCardView = view.findViewById(R.id.apk)
        apkInfoView.setCardBackgroundColor(cardColor)
        val signaturesView: MaterialCardView = view.findViewById(R.id.signatures)
        signaturesView.setCardBackgroundColor(cardColor)
        val missingLibsView: MaterialCardView = view.findViewById(R.id.missing_libs)
        missingLibsView.setCardBackgroundColor(cardColor)

        if (!FeatureController.isVirusTotalEnabled() || Prefs.VirusTotal.getApiKey() == null) {
            mVtContainerView!!.visibility = View.GONE
            view.findViewById<View>(R.id.vt_disclaimer).visibility = View.GONE
        }
        if (!FeatureController.isInternetEnabled()) {
            pithusContainerView.visibility = View.GONE
        }

        mViewModel!!.getApkChecksumsLiveData().observe(viewLifecycleOwner) { checksums ->
            if (checksums == null) return@observe
            val lines = mutableListOf<CharSequence>()
            for (digest in checksums) {
                lines.add(SpannableStringBuilder()
                    .append(getPrimaryText(mActivity!!, digest.first + LangUtils.getSeparatorString()))
                    .append(getMonospacedText(digest.second)))
            }
            view.findViewById<TextView>(R.id.apk_title).setText(R.string.apk_checksums)
            view.findViewById<TextView>(R.id.apk_description).text = TextUtilsCompat.joinSpannable("\n", lines)
        }
        mViewModel!!.getPackageInfoLiveData().observe(viewLifecycleOwner) { packageInfo ->
            if (packageInfo != null) {
                val archiveFilePath = mViewModel!!.getApkFile()!!.absolutePath
                val applicationInfo = packageInfo.applicationInfo
                applicationInfo.publicSourceDir = archiveFilePath
                applicationInfo.sourceDir = archiveFilePath
                mAppName = applicationInfo.loadLabel(mActivity!!.packageManager)
            } else {
                val apkFile = mViewModel!!.getApkFile()
                mAppName = if (apkFile != null) apkFile.name else mViewModel!!.getApkUri()!!.lastPathSegment
            }
            mActivity!!.title = mAppName
            mActivity!!.setSubtitle(R.string.scanner)
        }
        mViewModel!!.getApkVerifierResultLiveData().observe(viewLifecycleOwner) { result ->
            val checksumDescription: TextView = view.findViewById(R.id.checksum_description)
            val builder = SpannableStringBuilder()
            builder.append(PackageUtils.getApkVerifierInfo(result, mActivity!!))
            val certificates = result.signerCertificates
            if (!certificates.isNullOrEmpty()) {
                builder.append(getCertificateInfo(mActivity!!, certificates))
            }
            checksumDescription.text = builder
        }
        mViewModel!!.getAllClassesLiveData().observe(viewLifecycleOwner) { allClasses ->
            view.findViewById<TextView>(R.id.classes_title).setText(
                resources.getQuantityString(R.plurals.classes, allClasses.size, allClasses.size)
            )
            classesView.setOnClickListener { mActivity!!.loadNewFragment(ClassListingFragment()) }
        }
        mViewModel!!.getTrackerClassesLiveData().observe(viewLifecycleOwner) { trackerClasses ->
            setTrackerInfo(trackerClasses, view)
        }
        mViewModel!!.getLibraryClassesLiveData().observe(viewLifecycleOwner) { libraryClasses ->
            setLibraryInfo(libraryClasses, view)
            mActivity!!.showProgress(false)
        }
        mViewModel!!.getMissingClassesLiveData().observe(viewLifecycleOwner) { missingClasses ->
            if (missingClasses.isNotEmpty()) {
                view.findViewById<TextView>(R.id.missing_libs_title).setText(
                    resources.getQuantityString(R.plurals.missing_signatures, missingClasses.size, missingClasses.size)
                )
                missingLibsView.visibility = View.VISIBLE
                missingLibsView.setOnClickListener {
                    SearchableMultiChoiceDialogBuilder(mActivity!!, missingClasses, ArrayUtils.toCharSequence(missingClasses))
                        .setTitle(R.string.signatures)
                        .showSelectAll(false)
                        .setNegativeButton(R.string.ok, null)
                        .setNeutralButton(R.string.send_selected) { _, _, selectedItems ->
                            val message = "Package: ${mViewModel!!.packageName}
Signatures: $selectedItems"\nval i = Intent(Intent.ACTION_SEND).apply {
                                type = "message/rfc822"\nputExtra(Intent.EXTRA_EMAIL, arrayOf("am4android@riseup.net"))
                                putExtra(Intent.EXTRA_SUBJECT, "App Manager: Missing signatures")
                                putExtra(Intent.EXTRA_TEXT, message)
                            }
                            startActivity(Intent.createChooser(i, getText(R.string.signatures)))
                        }.show()
                }
            }
        }
        mViewModel!!.getVtFileUploadLiveData().observe(viewLifecycleOwner) { permalink ->
            if (permalink == null) {
                mVtTitleView!!.setText(R.string.vt_uploading)
                if (Prefs.VirusTotal.promptBeforeUpload()) {
                    MaterialAlertDialogBuilder(mActivity!!)
                        .setTitle(R.string.scan_in_vt)
                        .setMessage(R.string.vt_confirm_uploading_file)
                        .setCancelable(false)
                        .setPositiveButton(R.string.vt_confirm_upload_and_scan) { _, _ -> mViewModel!!.enableUploading() }
                        .setNegativeButton(R.string.no) { _, _ -> mViewModel!!.disableUploading() }
                        .show()
                } else mViewModel!!.enableUploading()
            } else {
                mVtTitleView!!.setText(R.string.vt_queued)
                mVtDescriptionView!!.text = permalink
            }
        }
        mViewModel!!.getVtFileReportLiveData().observe(viewLifecycleOwner) { vtFileReport ->
            if (vtFileReport == null) {
                mVtTitleView!!.setText(R.string.vt_failed)
                mVtDescriptionView!!.text = null
                mVtContainerView!!.setOnClickListener(null)
            } else {
                publishVirusTotalReport(vtFileReport)
            }
        }
        mViewModel!!.getPithusReportLiveData().observe(viewLifecycleOwner) { url ->
            if (url != null) pithusDescriptionView!!.text = url
            else pithusDescriptionView!!.setText(R.string.report_not_available)
        }
    }

    private fun publishVirusTotalReport(vtFileReport: VtFileReport) {
        val positives = vtFileReport.positives
        val resultSummary = getString(R.string.vt_success, positives, vtFileReport.total)
        @ColorInt
        val color = when {
            positives <= 3 -> ColorCodes.getVirusTotalSafeIndicatorColor(mActivity!!)
            positives <= 12 -> ColorCodes.getVirusTotalUnsafeIndicatorColor(mActivity!!)
            else -> ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(mActivity!!)
        }
        val scanDate = getString(R.string.vt_scan_date, DateUtils.formatDateTime(mActivity!!, vtFileReport.scanDate))
        val permalink = vtFileReport.permalink
        val result: Spanned?
        val vtFileReportScanItems = vtFileReport.results
        if (vtFileReportScanItems.isNotEmpty()) {
            val colorUnsafe = ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(mActivity!!)
            val colorSafe = ColorCodes.getVirusTotalSafeIndicatorColor(mActivity!!)
            val detectedList = ArrayList<Spannable>()
            val suspiciousList = ArrayList<Spannable>()
            val undetectedList = ArrayList<Spannable>()
            val neutralList = ArrayList<Spannable>()
            for (item in vtFileReportScanItems) {
                val sb = SpannableStringBuilder()
                val title = getPrimaryText(mActivity!!, item.engineName)
                if (item.category < VtAvEngineResult.CAT_UNDETECTED) {
                    sb.append(title)
                    neutralList.add(sb)
                } else if (item.category < VtAvEngineResult.CAT_SUSPICIOUS) {
                    sb.append(getColoredText(title, colorSafe))
                    undetectedList.add(sb)
                } else if (item.category == VtAvEngineResult.CAT_SUSPICIOUS) {
                    sb.append(getColoredText(title, colorUnsafe))
                    suspiciousList.add(sb)
                } else { // malicious
                    sb.append(getColoredText(title, colorUnsafe))
                    detectedList.add(sb)
                }
                sb.append(getSmallerText(" (${item.engineVersion})"))
                if (item.result != null) {
                    sb.append("\n").append(item.result)
                }
            }
            detectedList.addAll(suspiciousList)
            detectedList.addAll(undetectedList)
            detectedList.addAll(neutralList)
            result = UiUtils.getOrderedList(detectedList)
        } else result = null
        mVtTitleView!!.text = getColoredText(resultSummary, color)
        if (result != null) {
            mVtDescriptionView!!.setText(R.string.tap_to_see_details)
            mVtContainerView!!.setOnClickListener {
                VirusTotalDialog.getInstance(resultSummary, scanDate, result, permalink)
                    .show(parentFragmentManager, VirusTotalDialog.TAG)
            }
        }
    }

    private fun getNativeLibraryInfo(trackerOnly: Boolean): Map<String, SpannableStringBuilder> {
        val nativeLibsInApk: Collection<String> = mViewModel!!.getNativeLibraries() ?: return emptyMap()
        if (nativeLibsInApk.isEmpty()) return HashMap()
        val libNames = resources.getStringArray(R.array.lib_native_names)
        val libSignatures = resources.getStringArray(R.array.lib_native_signatures)
        val isTracker = resources.getIntArray(R.array.lib_native_is_tracker)
        val matchedLibs = arrayOfNulls<MutableList<String>>(libSignatures.size)
        val foundNativeLibInfoMap: MutableMap<String, SpannableStringBuilder> = ArrayMap()
        for (i in libSignatures.indices) {
            if (trackerOnly && isTracker[i] == 0) continue
            val pattern = Pattern.compile(libSignatures[i])
            for (lib in nativeLibsInApk) {
                if (pattern.matcher(lib).find()) {
                    if (matchedLibs[i] == null) {
                        matchedLibs[i] = ArrayList()
                    }
                    matchedLibs[i]!!.add(lib)
                }
            }
            if (matchedLibs[i] == null) continue
            var builder = foundNativeLibInfoMap[libNames[i]]
            if (builder == null) {
                builder = SpannableStringBuilder(getPrimaryText(mActivity!!, libNames[i]))
                foundNativeLibInfoMap[libNames[i]] = builder
            }
            for (lib in matchedLibs[i]!!) {
                builder.append("\n").append(getMonospacedText(lib))
            }
        }
        return foundNativeLibInfoMap
    }

    private fun setTrackerInfo(trackerInfoList: List<SignatureInfo>, view: View) {
        val foundTrackerInfoMap: MutableMap<String, SpannableStringBuilder> = ArrayMap()
        foundTrackerInfoMap.putAll(getNativeLibraryInfo(true))
        var hasSecondDegree = false
        for (trackerInfo in trackerInfoList) {
            if (foundTrackerInfoMap[trackerInfo.label] == null) {
                foundTrackerInfoMap[trackerInfo.label] = SpannableStringBuilder()
                    .append(getPrimaryText(mActivity!!, trackerInfo.label))
            }
            foundTrackerInfoMap[trackerInfo.label]!!
                .append("\n")
                .append(getMonospacedText(trackerInfo.signature))
                .append(getSmallerText(" (${trackerInfo.count})"))
            if (!hasSecondDegree) {
                hasSecondDegree = trackerInfo.label.startsWith("²")
            }
        }
        val foundTrackerNames = foundTrackerInfoMap.keys
        val foundTrackerInfo = ArrayList(foundTrackerInfoMap.values)
        Collections.sort(foundTrackerInfo) { o1, o2 -> o1.toString().compareToIgnoreCase(o2.toString()) }
        val trackerList = SpannableStringBuilder(UiUtils.getOrderedList(foundTrackerInfo))
        val foundTrackerList = SpannableStringBuilder()
        val totalTrackersFound = foundTrackerInfoMap.size
        if (totalTrackersFound > 0) {
            foundTrackerList.append(getString(R.string.found_trackers)).append(" ")
                .append(TextUtilsCompat.joinSpannable(", ", foundTrackerNames))
        }
        val totalTrackerClasses = mViewModel!!.trackerClasses?.size ?: 0
        val summary = when (totalTrackersFound) {
            0 -> getString(R.string.no_tracker_found)
            1 -> resources.getQuantityString(R.plurals.tracker_and_classes, totalTrackerClasses, totalTrackerClasses)
            2 -> resources.getQuantityString(R.plurals.two_trackers_and_classes, totalTrackerClasses, totalTrackerClasses)
            else -> resources.getQuantityString(R.plurals.other_trackers_and_classes, totalTrackersFound, totalTrackersFound, totalTrackerClasses)
        }
        val coloredSummary = if (totalTrackersFound == 0) {
            getColoredText(summary, ColorCodes.getScannerNoTrackerIndicatorColor(mActivity!!))
        } else {
            getColoredText(summary, ColorCodes.getScannerTrackerIndicatorColor(mActivity!!))
        }

        val trackerInfoTitle: TextView = view.findViewById(R.id.tracker_title)
        val trackerInfoDescription: TextView = view.findViewById(R.id.tracker_description)
        trackerInfoTitle.text = coloredSummary
        if (totalTrackersFound == 0) {
            trackerInfoDescription.visibility = View.GONE
            return
        }
        trackerInfoDescription.visibility = View.VISIBLE
        trackerInfoDescription.text = foundTrackerList
        val trackersView: MaterialCardView = view.findViewById(R.id.tracker)
        trackersView.setOnClickListener {
            TrackerInfoDialog.getInstance(coloredSummary, trackerList, hasSecondDegree)
                .show(parentFragmentManager, TrackerInfoDialog.TAG)
        }
    }

    private fun setLibraryInfo(libraryInfoList: List<SignatureInfo>, view: View) {
        val foundLibInfoMap: MutableMap<String, SpannableStringBuilder> = ArrayMap()
        foundLibInfoMap.putAll(getNativeLibraryInfo(false))
        for (libraryInfo in libraryInfoList) {
            if (foundLibInfoMap[libraryInfo.label] == null) {
                foundLibInfoMap[libraryInfo.label] = SpannableStringBuilder()
                    .append(getPrimaryText(mActivity!!, libraryInfo.label))
                    .append(getSmallerText(" (${libraryInfo.type})"))
            }
            foundLibInfoMap[libraryInfo.label]!!
                .append("\n")
                .append(getMonospacedText(libraryInfo.signature))
                .append(getSmallerText(" (${libraryInfo.count})"))
        }
        val foundLibNames = foundLibInfoMap.keys
        val foundLibInfoList = ArrayList(foundLibInfoMap.values)
        val totalLibsFound = foundLibInfoList.size
        Collections.sort(foundLibInfoList) { o1, o2 -> o1.toString().compareToIgnoreCase(o2.toString()) }
        val foundLibsInfo = UiUtils.getOrderedList(foundLibInfoList)
        val summary = if (totalLibsFound == 0) {
            getString(R.string.no_libs)
        } else {
            resources.getQuantityString(R.plurals.libraries, totalLibsFound, totalLibsFound)
        }

        view.findViewById<TextView>(R.id.libs_title).text = summary
        view.findViewById<TextView>(R.id.libs_description).text = TextUtils.join(", ", foundLibNames)
        if (totalLibsFound == 0) return
        val libsView: MaterialCardView = view.findViewById(R.id.libs)
        libsView.setOnClickListener {
            LibraryInfoDialog.getInstance(summary, foundLibsInfo)
                .show(parentFragmentManager, LibraryInfoDialog.TAG)
        }
    }

    companion object {
        private val SIG_TO_IGNORE: Pattern = Pattern.compile("^(android(|x)|com\.android|com\.google\.android|java(|x)|j\$\.(util|time)|\w\d?(\.\w\d?)+)\..*$")

        private fun getCertificateInfo(context: Context, certificates: List<X509Certificate>): Spannable {
            val builder = SpannableStringBuilder()
            for (cert in certificates) {
                try {
                    if (builder.isNotEmpty()) builder.append("\n")
                    builder.append(getPrimaryText(context, context.getString(R.string.issuer) + LangUtils.getSeparatorString()))
                        .append(cert.issuerX500Principal.name).append("\n")
                        .append(getPrimaryText(context, context.getString(R.string.algorithm) + LangUtils.getSeparatorString()))
                        .append(cert.sigAlgName).append("\n")
                    builder.append(getPrimaryText(context, context.getString(R.string.checksums)))
                    val digests = DigestUtils.getDigests(cert.encoded)
                    for (digest in digests) {
                        builder.append("\n")
                            .append(getPrimaryText(context, digest.first + LangUtils.getSeparatorString()))
                            .append(getMonospacedText(digest.second))
                    }
                } catch (e: CertificateEncodingException) {
                    e.printStackTrace()
                }
            }
            return builder
        }
    }
}
