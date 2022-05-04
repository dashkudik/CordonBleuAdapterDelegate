package com.example.cordonbleuadapterdelegate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@OptIn(ExperimentalStdlibApi::class)
class CordonBleuAdapterDelegate<T> private constructor() :
    ReadOnlyProperty<Fragment, CordonBleuAdapterDelegate<T>.CordonBleuListAdapter> {

    companion object {
        fun <T> cordonBleuAdapter(): CordonBleuAdapterDelegate<T> {
            return CordonBleuAdapterDelegate()
        }

        private fun <T> defaultDiffUtil() = object: DiffUtil.ItemCallback<T>() {
            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem == newItem
            }

            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem == newItem
            }
        }
    }

    /**
     *  KEY - ViewType
     *  VALUE - ViewHolder
     */
    private var mViewHolders: HashMap<Int, ViewHolder<T, ViewBinding>> = HashMap(8, 1f)

    private val mAdapter: CordonBleuListAdapter by lazy(LazyThreadSafetyMode.NONE) {
        CordonBleuListAdapter(mDiffUtil ?: defaultDiffUtil())
    }

    private var mViewTypeOfPosition: ((Int) -> Int)? = null

    private var mPagingConfig: PagingConfig<T>? = null

    private var mDiffUtil: DiffUtil.ItemCallback<T>? = null

    override fun getValue(
        thisRef: Fragment,
        property: KProperty<*>
    ): CordonBleuListAdapter {
        return mAdapter
    }

    fun diffUtil(diffUtil: DiffUtil.ItemCallback<T>): CordonBleuAdapterDelegate<T> {
        mDiffUtil = diffUtil
        return this
    }

    fun viewTypeOfPosition(block: (Int) -> Int): CordonBleuAdapterDelegate<T> {
        mViewTypeOfPosition = block
        return this
    }

    fun enableAutomaticLoadingRetrying(timeoutMillis: Int): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.loadingRetryTimeout = timeoutMillis
        return this
    }

    fun onPortionRequired(onPortionRequired: suspend (numberOfRequiredPage: Int, offset: Int) -> List<T>): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.onPortionRequired = onPortionRequired
        return this
    }

    fun pageSize(pageSize: Int): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.pageSize = pageSize
        return this
    }

    fun prefetchDistance(prefetchDistance: Int): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.prefetchDistance = prefetchDistance
        return this
    }

    fun onPortionLoading(onPortionLoading: (numberOfRequiredPage: Int, offset: Int) -> Unit): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.onPortionLoading = onPortionLoading
        return this
    }

    fun onPortionLoaded(onPortionLoaded: ((throwable: Throwable?, numberOfRequiredPage: Int, offset: Int) -> Unit)): CordonBleuAdapterDelegate<T> {
        if (mPagingConfig == null) {
            mPagingConfig = PagingConfig()
        }
        mPagingConfig?.onPortionLoaded = onPortionLoaded
        return this
    }

    fun <VB : ViewBinding> viewHolder(
        @LayoutRes layoutId: Int,
        binder: (View) -> VB,
        onBind: VB.(model: T, position: Int) -> Unit,
        clicks: Array<ViewHolder.ViewHolderClick<T>> = emptyArray(),
        clipChildren: Boolean = true,
    ): CordonBleuAdapterDelegate<T> {
        mViewHolders[layoutId] =
            ViewHolder(layoutId, binder, onBind, clicks) as ViewHolder<T, ViewBinding>
        return this
    }

    class ViewHolder<T, VB : ViewBinding>(
        @LayoutRes val layoutId: Int,
        val binder: (View) -> VB,
        val onBind: VB.(model: T, position: Int) -> Unit,
        val clicks: Array<ViewHolderClick<T>>
    ) {
        @JvmInline
        value class ViewHolderClick<in T>(val click: Pair<Int, (T) -> Unit>) {
            val viewId get() = click.first
            val onClick inline get() = click.second
        }

        companion object {
            infix fun <T> Int.click(other: (T) -> Unit): ViewHolderClick<T> {
                return ViewHolderClick(this to other)
            }
        }
    }

    data class PagingConfig<T>(
        var pageSize: Int = DEFAULT_PAGE_SIZE,
        var prefetchDistance: Int = (pageSize * DEFAULT_PREFETCH_COEFFICIENT).toInt(),
        var onPortionRequired: (suspend (numberOfRequiredPage: Int, offset: Int) -> List<T>)? = null,
        var onPortionLoading: ((numberOfRequiredPage: Int, offset: Int) -> Unit)? = null,
        var onPortionLoaded: ((throwable: Throwable?, numberOfRequiredPage: Int, offset: Int) -> Unit)? = null,
        var loadingRetryTimeout: Int? = null
    ) {

        init {
            if (prefetchDistance >= pageSize) throw IllegalArgumentException("'prefetchDistance' must be less than 'pageSize'")
            if (pageSize > MAX_PAGE_SIZE) throw IllegalArgumentException("500 must not be less than 'pageSize'")
        }

        companion object {
            const val DEFAULT_PREFETCH_COEFFICIENT = 0.4f
            const val DEFAULT_PAGE_SIZE = 30
            const val MAX_PAGE_SIZE = 500
        }
    }

    inner class CordonBleuListAdapter internal constructor(diffUtil: DiffUtil.ItemCallback<T>) :
        ListAdapter<T, CordonBleuListAdapter.CordonBleuViewHolder<T>>(diffUtil) {

        private val pagingScope = CoroutineScope(IO)

        private var pageCount: Int = 0
        private var isPaginationExhausted: Boolean = false

        private var lastPortionLoadingJob: Job? = null
        private var lastPortionLoadingBlock: (suspend () -> Unit)? = null

        override fun getItemViewType(position: Int): Int {
            return mViewTypeOfPosition?.invoke(position) ?: super.getItemViewType(position)
        }

        override fun onBindViewHolder(holder: CordonBleuViewHolder<T>, position: Int) {
            performPortionLoadingIfNeed(holder.bindingAdapterPosition)
            holder.onBind(getItem(position), position)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): CordonBleuViewHolder<T> {
            val viewHolder: ViewHolder<T, ViewBinding> = mViewHolders[viewType]
                ?: throw IllegalArgumentException("No ViewHolder found for ViewType $viewType")
            val view =
                LayoutInflater.from(parent.context).inflate(viewHolder.layoutId, parent, false)
            return CordonBleuViewHolder(viewHolder, view)
        }

        private fun performPortionLoadingIfNeed(bindingPosition: Int) {
            mPagingConfig?.let {
                val isPrefetchDistanceExceeded =
                    itemCount - bindingPosition < it.prefetchDistance
                val hasLoadingAlreadyBeenProceed = lastPortionLoadingJob?.isActive?.equals(false) == false
                if (isPrefetchDistanceExceeded && isPaginationExhausted.not() && hasLoadingAlreadyBeenProceed.not()) {
                    lastPortionLoadingBlock = lastPortionLoadingBlock@{
                        it.onPortionLoading?.invoke(pageCount + 1, it.pageSize * (pageCount))
                        val awaitedPortion = runCatching {
                            it.onPortionRequired?.invoke(pageCount + 1, it.pageSize * (pageCount)) ?: throw IllegalArgumentException("'onPortionRequired' has not been provided!")
                        }.getOrElse { throwable ->
                            it.onPortionLoaded?.invoke(throwable, pageCount + 1, it.pageSize * (pageCount))
                            it.loadingRetryTimeout?.let {
                                delay(it.toLong())
                                retryLastPortionLoading()
                            }
                            return@lastPortionLoadingBlock
                        }
                        withContext(Main.immediate) {
                            append(awaitedPortion)
                        }
                        if (awaitedPortion.isNotEmpty()) {
                            it.onPortionLoaded?.invoke(null, pageCount + 1, it.pageSize * (pageCount))
                            pageCount++
                        }
                        isPaginationExhausted = awaitedPortion.isEmpty()
                    }
                    lastPortionLoadingJob = pagingScope.launch {
                        lastPortionLoadingBlock?.invoke()
                    }
                }
            }
        }

        fun getPageCount(): Int {
            return pageCount
        }

        fun getPageSize(): Int {
            return mPagingConfig?.pageSize ?: -1
        }

        fun resetPage() {
            pageCount = 0
        }

        fun append(items: List<T>) {
            val copyOfCurrentList = currentList.toMutableList()
            copyOfCurrentList.addAll(items)
            submitList(copyOfCurrentList)
        }

        fun changeAt(index: Int, newItem: T) {
            val copyOfCurrentList = currentList.toMutableList()
            copyOfCurrentList[index] = newItem
            submitList(copyOfCurrentList)
        }

        fun retryLastPortionLoading() {
            pagingScope.launch {
                lastPortionLoadingBlock?.invoke()
            }
        }

        fun notifyRecyclerIsDestroyed() {
            pagingScope.cancel()
        }

        inner class CordonBleuViewHolder<T>(
            private val viewHolder: ViewHolder<T, ViewBinding>,
            view: View
        ) : RecyclerView.ViewHolder(view) {

            private val binding = viewHolder.binder(view)
            private var model: T? = null

            init {
                viewHolder.clicks.forEach { viewHolderClick ->
                    itemView.findViewById<View>(viewHolderClick.viewId)
                        .setOnClickListener {
                            viewHolderClick.onClick(
                                model ?: return@setOnClickListener
                            )
                        }
                }
            }

            fun onBind(model: T, position: Int) {
                this.model = model
                val bindsBlock = viewHolder.onBind
                binding.bindsBlock(model, position)
            }
        }
    }
}