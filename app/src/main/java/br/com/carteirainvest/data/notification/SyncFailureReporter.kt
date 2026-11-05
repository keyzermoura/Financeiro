package br.com.carteirainvest.data.notification

/** Small Android-free seam so Worker tests can assert notifications without a device. */
interface SyncFailureReporter {
    fun notifyFailure(label: String, message: String)
    fun clear()
}
