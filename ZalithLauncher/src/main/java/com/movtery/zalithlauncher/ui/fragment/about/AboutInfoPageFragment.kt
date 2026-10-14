package com.movtery.zalithlauncher.ui.fragment.about

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentAboutInfoPageBinding
import com.movtery.zalithlauncher.ui.subassembly.about.AboutItemBean
import com.movtery.zalithlauncher.ui.subassembly.about.AboutRecyclerAdapter
import com.movtery.zalithlauncher.utils.ZHTools

class AboutInfoPageFragment : Fragment(R.layout.fragment_about_info_page) {
    private lateinit var binding: FragmentAboutInfoPageBinding
    private val mAboutData: MutableList<AboutItemBean> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAboutInfoPageBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadAboutData(requireContext().resources)
        val context = requireActivity()

        binding.apply {
            dec1.text = "XUI LAUNCHER"
            dec2.text = "by XRANS OFFICIAL"
            dec3.text = "Blood Gradient Edition\nA community-built launcher fork for Minecraft: Java Edition, with a dark interface and blood-red accents.\nOpen source under GPL-3.0. See LICENSE and NOTICE for license details and upstream attribution."

            licenseButton.setOnClickListener {
                ZHTools.openLink(requireActivity(), "https://www.gnu.org/licenses/gpl-3.0.html")
            }
            qqGroupButton.visibility = View.GONE

            val aboutAdapter = AboutRecyclerAdapter(mAboutData)
            aboutRecycler.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = aboutAdapter
                isNestedScrollingEnabled = false
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadAboutData(resources: Resources) {
        mAboutData.clear()
        val placeholderIcon = resources.getDrawable(R.drawable.ic_help, requireContext().theme)
        mAboutData.add(
            AboutItemBean(
                placeholderIcon,
                "Xrans Official",
                "Modder Fork · Telegram: https://t.me/xrans_offc · TikTok: https://www.tiktok.com/@1shadow_dx",
                null
            )
        )
        mAboutData.add(
            AboutItemBean(
                placeholderIcon,
                "All Team Pojav Launcher",
                "Based Launcher · github.com/PojavLauncherTeam",
                null
            )
        )
        mAboutData.add(
            AboutItemBean(
                placeholderIcon,
                "All Team Zalith Launcher",
                "Based Launcher · github.com/ZalithLauncher",
                null
            )
        )
    }
}