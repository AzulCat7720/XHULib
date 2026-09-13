package com.xhulib.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.LabeledValue
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * 「发现」页（底部导航第二个一级页面）三个栏目的数据。
 *
 * 三个栏目各自持有一份 [UiState]，互不影响：某一个栏目加载失败，
 * 不会影响另外两个栏目的展示。栏目都在首次进入对应标签时才加载。
 */
class DiscoverViewModel(private val repository: OpacRepository) : ViewModel() {

    // ------------------------------------------------------------ 分类浏览

    private val _classes = MutableStateFlow<UiState<List<LabeledValue>>>(UiState.Loading)
    val classes: StateFlow<UiState<List<LabeledValue>>> = _classes.asStateFlow()

    private val _selectedClass = MutableStateFlow<LabeledValue?>(null)
    val selectedClass: StateFlow<LabeledValue?> = _selectedClass.asStateFlow()

    private val _books = MutableStateFlow<UiState<List<BookItem>>>(UiState.Success(emptyList()))
    val books: StateFlow<UiState<List<BookItem>>> = _books.asStateFlow()

    /** 当前大类下的子类（可能为空，表示该大类没有下级）。 */
    private val _subClasses = MutableStateFlow<List<LabeledValue>>(emptyList())
    val subClasses: StateFlow<List<LabeledValue>> = _subClasses.asStateFlow()

    /** 选中的子类；为 null 表示看整个大类。 */
    private val _selectedSubClass = MutableStateFlow<LabeledValue?>(null)
    val selectedSubClass: StateFlow<LabeledValue?> = _selectedSubClass.asStateFlow()

    private var classesRequested = false
    private var booksJob: Job? = null

    /** 首次进入「分类浏览」标签时调用；重复调用不会重复请求。 */
    fun ensureClasses() {
        if (classesRequested) return
        reloadClasses()
    }

    fun reloadClasses() {
        classesRequested = true
        viewModelScope.launch {
            _classes.value = UiState.Loading
            _selectedClass.value = null
            _books.value = UiState.Success(emptyList())
            val state = loadState { repository.browseTree() }
            _classes.value = state
            // 分类树到手后默认选中第一个分类，省去用户一次点击
            if (state is UiState.Success) state.data.firstOrNull()?.let { selectClass(it) }
        }
    }

    /** 选子类：用子类号重新取书目。 */
    fun selectSubClass(item: LabeledValue?) {
        _selectedSubClass.value = item
        reloadBooks()
    }

    /** 当前实际用于查询的分类号：选了子类就用子类。 */
    private fun effectiveClassNo(): String? =
        _selectedSubClass.value?.value ?: _selectedClass.value?.value

    private fun loadSubClasses(parent: String) {
        viewModelScope.launch {
            _subClasses.value = runCatching { repository.browseSubClasses(parent) }
                .getOrDefault(emptyList())
        }
    }

    fun selectClass(item: LabeledValue) {
        _selectedClass.value = item
        _selectedSubClass.value = null
        loadSubClasses(item.value)
        loadBooks()
    }

    private fun loadBooks() {
        booksJob?.cancel()
        booksJob = viewModelScope.launch {
            _books.value = UiState.Loading
            _books.value = loadState { repository.browseBooks(effectiveClassNo()) }
        }
    }

    fun reloadBooks() = loadBooks()

    // ------------------------------------------------------------ 新书通报

    private val _newClasses = MutableStateFlow<UiState<List<LabeledValue>>>(UiState.Loading)
    val newClasses: StateFlow<UiState<List<LabeledValue>>> = _newClasses.asStateFlow()

    private val _selectedNewClass = MutableStateFlow<LabeledValue?>(null)
    val selectedNewClass: StateFlow<LabeledValue?> = _selectedNewClass.asStateFlow()

    private val _newBooks = MutableStateFlow<UiState<List<BookItem>>>(UiState.Success(emptyList()))
    val newBooks: StateFlow<UiState<List<BookItem>>> = _newBooks.asStateFlow()

    /** 新书通报的子类。 */
    private val _newSubClasses = MutableStateFlow<List<LabeledValue>>(emptyList())
    val newSubClasses: StateFlow<List<LabeledValue>> = _newSubClasses.asStateFlow()

    private val _selectedNewSubClass = MutableStateFlow<LabeledValue?>(null)
    val selectedNewSubClass: StateFlow<LabeledValue?> = _selectedNewSubClass.asStateFlow()

    private var newClassesRequested = false
    private var newBooksJob: Job? = null

    /** 首次进入「新书通报」标签时调用。 */
    fun ensureNewBooks() {
        if (newClassesRequested) return
        reloadNewClasses()
    }

    fun reloadNewClasses() {
        newClassesRequested = true
        viewModelScope.launch {
            _newClasses.value = UiState.Loading
            _selectedNewClass.value = null
            _newBooks.value = UiState.Success(emptyList())
            val state = loadState { repository.newBookTree() }
            _newClasses.value = state
            if (state is UiState.Success) state.data.firstOrNull()?.let { selectNewClass(it) }
        }
    }

    fun selectNewSubClass(item: LabeledValue?) {
        _selectedNewSubClass.value = item
        reloadNewBooks()
    }

    private fun loadNewSubClasses(parent: String) {
        viewModelScope.launch {
            _newSubClasses.value = runCatching { repository.browseSubClasses(parent) }
                .getOrDefault(emptyList())
        }
    }

    private fun loadNewBookList() {
        newBooksJob?.cancel()
        newBooksJob = viewModelScope.launch {
            _newBooks.value = UiState.Loading
            _newBooks.value = loadState {
                repository.newBooks(effectiveNewClassNo())
            }
        }
    }

    private fun effectiveNewClassNo(): String? =
        _selectedNewSubClass.value?.value ?: _selectedNewClass.value?.value

    fun selectNewClass(item: LabeledValue) {
        _selectedNewClass.value = item
        _selectedNewSubClass.value = null
        loadNewSubClasses(item.value)
        loadNewBookList()
    }

    fun reloadNewBooks() = loadNewBookList()

    // ------------------------------------------------------------ 学科参考

    private val _subjects = MutableStateFlow<UiState<List<LabeledValue>>>(UiState.Loading)
    val subjects: StateFlow<UiState<List<LabeledValue>>> = _subjects.asStateFlow()

    private val _selectedSubject = MutableStateFlow<LabeledValue?>(null)
    val selectedSubject: StateFlow<LabeledValue?> = _selectedSubject.asStateFlow()

    private val _subjectBooks = MutableStateFlow<UiState<List<BookItem>>>(UiState.Success(emptyList()))
    val subjectBooks: StateFlow<UiState<List<BookItem>>> = _subjectBooks.asStateFlow()

    private var subjectsRequested = false
    private var subjectBooksJob: Job? = null

    /** 首次进入「学科参考」标签时调用。 */
    fun ensureSubjects() {
        if (subjectsRequested) return
        reloadSubjects()
    }

    fun reloadSubjects() {
        subjectsRequested = true
        viewModelScope.launch {
            _subjects.value = UiState.Loading
            _selectedSubject.value = null
            _subjectBooks.value = UiState.Success(emptyList())
            _subjects.value = loadState { repository.subjectCatalog() }
        }
    }

    /**
     * 学科导航只给学科名，没有现成的书目接口，
     * 因此用学科中文名做一次「主题词」检索。
     */
    fun selectSubject(item: LabeledValue) {
        _selectedSubject.value = item
        subjectBooksJob?.cancel()
        subjectBooksJob = viewModelScope.launch {
            _subjectBooks.value = UiState.Loading
            _subjectBooks.value = loadState {
                repository.search(item.label, searchType = SUBJECT_SEARCH_TYPE)
            }
        }
    }

    fun reloadSubjectBooks() {
        _selectedSubject.value?.let { selectSubject(it) }
    }

    /** 从学科书目回到学科列表。 */
    fun clearSubject() {
        subjectBooksJob?.cancel()
        _selectedSubject.value = null
        _subjectBooks.value = UiState.Success(emptyList())
    }

    private companion object {
        /** 学科名走主题词检索。 */
        const val SUBJECT_SEARCH_TYPE = "keyword"

        val CLASS_NO_PARAM = Regex("""[?&](?:cls_no|cls|class_no|classNo|clsNo)=([^&]*)""")
    }

    /**
     * 从分类树条目里取出可直接传给书目页的分类号。
     *
     * `parseClassTree` 对分类浏览 / 新书通报返回的 `value` 本身就是分类号（如 `A`），
     * 学科导航等回退分支返回的则是相对链接，这里两种形态都兼容：
     * 先从链接的 `cls` / `cls_no` 参数里取，取不到再判断它像不像分类号。
     *
     * 都不像时返回 null，仓库会退回「不带分类筛选」的列表页——
     * 宁可多显示一些书目，也不要拼出一个必然 404 的地址。
     */
    private fun classNoOf(value: String): String? {
        val raw = value.trim()
        if (raw.isEmpty()) return null

        if (raw.contains('?')) {
            val matched = CLASS_NO_PARAM.find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            if (matched.isNotEmpty()) {
                return runCatching { URLDecoder.decode(matched, "UTF-8") }.getOrDefault(matched)
            }
        }

        // 含「/」「.php」「:」的当它是地址而不是分类号
        val looksLikeUrl = raw.contains('/') || raw.contains(".php") || raw.contains(':')
        return if (looksLikeUrl) null else raw
    }
}
