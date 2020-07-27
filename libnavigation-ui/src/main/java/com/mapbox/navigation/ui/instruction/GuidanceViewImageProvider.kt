package com.mapbox.navigation.ui.instruction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mapbox.api.directions.v5.models.BannerComponents
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.navigation.utils.internal.JobControl
import com.mapbox.navigation.utils.internal.ThreadController
import com.mapbox.navigation.utils.internal.ifNonNull
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The class serves as a medium to emit bitmaps for the respective guidance view URL embedded in
 * [BannerInstructions]
 * @constructor
 */
class GuidanceViewImageProvider() {

    companion object {
        private val USER_AGENT_KEY = "User-Agent"
        private val USER_AGENT_VALUE = "MapboxJava/"
    }

    private val mainJobController: JobControl by lazy { ThreadController.getMainScopeAndRootJob() }
    private val okHttpClient = OkHttpClient.Builder().addInterceptor { chain: Interceptor.Chain ->
        chain.proceed(
            chain.request().newBuilder().addHeader(USER_AGENT_KEY, USER_AGENT_VALUE).build()
        )
    }.build()

    /**
     * The API reads the bannerInstruction and returns a guidance view bitmap if one is available
     * @param bannerInstructions [BannerInstructions]
     * @param callback [OnGuidanceImageDownload] Callback that is triggered based on appropriate state of image downloading
     */
    fun renderGuidanceView(
        bannerInstructions: BannerInstructions,
        callback: OnGuidanceImageDownload
    ) {
        val bannerView = bannerInstructions.view()
        ifNonNull(bannerView) { view ->
            val bannerComponents = view.components()
            ifNonNull(bannerComponents) { components ->
                components.forEachIndexed { _, component ->
                    component.takeIf { c -> c.type() == BannerComponents.GUIDANCE_VIEW }?.let {
                        ifNonNull(it.imageUrl()) { url ->
                            mainJobController.scope.launch {
                                val response = getBitmap(url)
                                response.bitmap?.let { b ->
                                    callback.onGuidanceImageReady(b)
                                } ?: callback.onFailure(response.error)
                            }
                        } ?: callback.onFailure("Guidance View Image URL is null")
                    }
                }
            } ?: callback.onNoGuidanceImageUrl()
        } ?: callback.onNoGuidanceImageUrl()
    }

    /**
     * The API allows you to cancel the rendering of guidance view.
     */
    fun cancelRender() {
        mainJobController.job.cancelChildren()
    }

    private suspend fun getBitmap(url: String): GuidanceViewImageResponse =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).build()
            try {
                val res = okHttpClient.newCall(req).execute()
                val body = res.body()
                val message = res.message()
                val code = res.code()
                res.close()
                when (code) {
                    401 -> GuidanceViewImageResponse(null, message)
                    200 -> GuidanceViewImageResponse(
                        BitmapFactory.decodeStream(
                            body?.byteStream()
                        ), "Something went wrong. Bitmap not received"
                    )
                    else -> GuidanceViewImageResponse(null, "Something went wrong. Bitmap not received")
                }
            } catch (exception: IOException) {
                GuidanceViewImageResponse(null, "Something went wrong. Bitmap not received")
            }
        }

    internal data class GuidanceViewImageResponse(val bitmap: Bitmap?, val error: String?)

    /**
     * Callback that is triggered based on appropriate state of image downloading
     */
    interface OnGuidanceImageDownload {
        /**
         * Triggered when the image has been downloaded and is ready to be used.
         * @param bitmap Bitmap
         */
        fun onGuidanceImageReady(bitmap: Bitmap)

        /**
         * Triggered when their is no URL to render
         */
        fun onNoGuidanceImageUrl()

        /**
         * Triggered when there is a failure to download the image
         * @param message String?
         */
        fun onFailure(message: String?)
    }
}
