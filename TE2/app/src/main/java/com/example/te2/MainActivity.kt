package com.example.te2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.content.ClipboardManager
import android.content.Context
import android.content.ClipData
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    
    private lateinit var editText: EditText
    private lateinit var toolbar: Toolbar
    private lateinit var tvCharCount: TextView
    private lateinit var tvWordCount: TextView
    private lateinit var tvLineCount: TextView
    private lateinit var tvCursorPos: TextView
    private var currentFileName: String? = null
    private var currentFileUri: Uri? = null
    private var currentFileExtension: String? = null
    private lateinit var textWatcher: TextWatcher
    
    // Text editing functionality
    private lateinit var clipboardManager: ClipboardManager
    private val textHistory = mutableListOf<String>()
    private var currentHistoryIndex = -1
    private val maxHistorySize = 50
    
    // Find and Replace functionality
    private var findReplaceDialog: AlertDialog? = null
    private var currentFindIndex = -1
    private var findResults = mutableListOf<Int>()
    private var currentFindText = ""
    private var currentReplaceText = ""
    private var isCaseSensitive = false
    private var isWholeWord = false
    
    // File picker launcher
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                openFile(uri)
            }
        }
    }
    
    // Syntax highlighting patterns
    private val kotlinPatterns = mapOf(
        "keywords" to Pattern.compile("\\b(fun|val|var|if|else|when|for|while|class|object|interface|enum|data|sealed|open|abstract|override|private|public|protected|internal|companion|init|constructor|super|this|return|break|continue|throw|try|catch|finally|as|is|in|!in|by|get|set)\\b"),
        "strings" to Pattern.compile("\"([^\"]|\\\")*\""),
        "comments" to Pattern.compile("//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/"),
        "numbers" to Pattern.compile("\\b\\d+(\\.\\d+)?\\b"),
        "types" to Pattern.compile("\\b(String|Int|Long|Float|Double|Boolean|Char|Byte|Short|Unit|Any|Nothing|List|Set|Map|Array|MutableList|MutableSet|MutableMap)\\b")
    )
    
    private val javaPatterns = mapOf(
        "keywords" to Pattern.compile("\\b(public|private|protected|static|final|abstract|class|interface|extends|implements|import|package|new|return|if|else|switch|case|default|for|while|do|break|continue|try|catch|finally|throw|throws|synchronized|volatile|transient|native|strictfp|enum|assert|const|goto)\\b"),
        "strings" to Pattern.compile("\"([^\"]|\\\")*\""),
        "comments" to Pattern.compile("//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/"),
        "numbers" to Pattern.compile("\\b\\d+(\\.\\d+)?[lLfFdD]?\\b"),
        "types" to Pattern.compile("\\b(String|int|long|float|double|boolean|char|byte|short|void|Object|Integer|Long|Float|Double|Boolean|Character|Byte|Short|List|Set|Map|ArrayList|HashSet|HashMap)\\b")
    )
    
    private val xmlPatterns = mapOf(
        "tags" to Pattern.compile("<[^>]+>"),
        "attributes" to Pattern.compile("\\w+\\s*=\\s*\"[^\"]*\""),
        "comments" to Pattern.compile("<!--[^-]*-->"),
        "strings" to Pattern.compile("\"([^\"]|\\\")*\"")
    )
    
    private val jsonPatterns = mapOf(
        "keys" to Pattern.compile("\"([^\"]+)\"\\s*:"),
        "strings" to Pattern.compile("\"([^\"]|\\\")*\""),
        "numbers" to Pattern.compile("\\b\\d+(\\.\\d+)?\\b"),
        "booleans" to Pattern.compile("\\b(true|false|null)\\b")
    )
    
    private val markdownPatterns = mapOf(
        "headers" to Pattern.compile("^#{1,6}\\s+.+$", Pattern.MULTILINE),
        "bold" to Pattern.compile("\\*\\*([^*]+)\\*\\*"),
        "italic" to Pattern.compile("\\*([^*]+)\\*"),
        "code" to Pattern.compile("`([^`]+)`"),
        "links" to Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
    )
    
    // Color scheme for syntax highlighting
    private val syntaxColors = mapOf(
        "keywords" to Color.parseColor("#FF6B6B"),      // Red
        "strings" to Color.parseColor("#4ECDC4"),      // Teal
        "comments" to Color.parseColor("#45B7D1"),     // Blue
        "numbers" to Color.parseColor("#96CEB4"),      // Green
        "types" to Color.parseColor("#FFEAA7")         // Yellow
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        editText = findViewById(R.id.edit_text)
        toolbar = findViewById(R.id.toolbar)
        tvCharCount = findViewById(R.id.tv_char_count)
        tvWordCount = findViewById(R.id.tv_word_count)
        tvLineCount = findViewById(R.id.tv_line_count)
        tvCursorPos = findViewById(R.id.tv_cursor_pos)
        
        // Initialize clipboard manager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // Set up toolbar
        setSupportActionBar(toolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Set up text editor
        setupTextEditor()
    }
    
    private fun setupTextEditor() {
        // Set text editor properties
        editText.hint = "Start typing your text here..."
        editText.isVerticalScrollBarEnabled = true
        editText.isHorizontalScrollBarEnabled = true
        
        // Create and add text change listener for syntax highlighting and undo/redo
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null) {
                    // Add to history for undo/redo
                    addToHistory(s.toString())
                    
                    // Update character and word counts
                    updateTextCounts(s.toString())
                    
                    // Update cursor position
                    updateCursorPosition()
                    
                    // Apply syntax highlighting if file type is supported
                    if (currentFileExtension != null) {
                        applySyntaxHighlighting(s.toString())
                    }
                }
            }
        }
        editText.addTextChangedListener(textWatcher)
        
        // Add touch listener to detect selection changes and cursor position
        editText.setOnTouchListener { _, _ ->
            // Update menu state when user touches the EditText (for selection changes)
            invalidateOptionsMenu()
            updateCursorPosition()
            false // Don't consume the touch event
        }
        
        // Initialize with empty text
        addToHistory("")
        
        // Initialize character and word count
        updateTextCounts("")
        
        // Initialize cursor position
        updateCursorPosition()
    }
    
    // Character, Word, and Line Counting functionality
    private fun updateTextCounts(text: String) {
        val charCount = text.length
        val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
        val lineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        
        tvCharCount.text = getString(R.string.char_count, charCount)
        tvWordCount.text = getString(R.string.word_count, wordCount)
        tvLineCount.text = getString(R.string.line_count, lineCount)
    }
    
    // Cursor position tracking
    private fun updateCursorPosition() {
        val position = editText.selectionStart
        tvCursorPos.text = "Pos: $position"
    }
    
    // Detailed text statistics
    private fun showDetailedStats() {
        val text = editText.text.toString()
        val charCount = text.length
        val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
        val lineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        val paragraphCount = if (text.isBlank()) 0 else text.split("\\n\\s*\\n".toRegex()).size
        val sentenceCount = if (text.isBlank()) 0 else text.split("[.!?]+\\s+".toRegex()).size
        
        val statsMessage = """
            ${getString(R.string.characters)}: $charCount
            ${getString(R.string.words)}: $wordCount
            ${getString(R.string.lines)}: $lineCount
            ${getString(R.string.paragraphs)}: $paragraphCount
            ${getString(R.string.sentences)}: $sentenceCount
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.detailed_stats))
            .setMessage(statsMessage)
            .setPositiveButton("OK", null)
            .show()
    }
    
    // Undo/Redo functionality
    private fun addToHistory(text: String) {
        // Remove any future history if we're not at the end
        if (currentHistoryIndex < textHistory.size - 1) {
            textHistory.subList(currentHistoryIndex + 1, textHistory.size).clear()
        }
        
        // Add new text to history
        textHistory.add(text)
        
        // Limit history size
        if (textHistory.size > maxHistorySize) {
            textHistory.removeAt(0)
        } else {
            currentHistoryIndex++
        }
        
        // Update menu state
        invalidateOptionsMenu()
    }
    
    private fun canUndo(): Boolean = currentHistoryIndex > 0
    
    private fun canRedo(): Boolean = currentHistoryIndex < textHistory.size - 1
    
    private fun undo() {
        if (canUndo()) {
            currentHistoryIndex--
            val text = textHistory[currentHistoryIndex]
            editText.removeTextChangedListener(textWatcher)
            editText.setText(text)
            editText.setSelection(text.length)
            editText.addTextChangedListener(textWatcher)
            updateTextCounts(text)
            updateCursorPosition()
        }
    }
    
    private fun redo() {
        if (canRedo()) {
            currentHistoryIndex++
            val text = textHistory[currentHistoryIndex]
            editText.removeTextChangedListener(textWatcher)
            editText.setText(text)
            editText.setSelection(text.length)
            editText.addTextChangedListener(textWatcher)
            updateTextCounts(text)
            updateCursorPosition()
        }
    }
    
    // Clipboard operations
    private fun copyText() {
        val selectedText = editText.text.substring(
            editText.selectionStart.coerceAtLeast(0),
            editText.selectionEnd.coerceAtMost(editText.text.length)
        )
        
        if (selectedText.isNotEmpty()) {
            val clip = ClipData.newPlainText("text", selectedText)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pasteText() {
        if (clipboardManager.hasPrimaryClip() && clipboardManager.primaryClip?.itemCount ?: 0 > 0) {
            val clipData = clipboardManager.primaryClip?.getItemAt(0)
            val textToPaste = clipData?.text?.toString() ?: ""
            
            if (textToPaste.isNotEmpty()) {
                val start = editText.selectionStart.coerceAtLeast(0)
                val end = editText.selectionEnd.coerceAtMost(editText.text.length)
                
                editText.text.replace(start, end, textToPaste)
                updateTextCounts(editText.text.toString())
                updateCursorPosition()
                Toast.makeText(this, "Text pasted from clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun cutText() {
        val selectedText = editText.text.substring(
            editText.selectionStart.coerceAtLeast(0),
            editText.selectionEnd.coerceAtMost(editText.text.length)
        )
        
        if (selectedText.isNotEmpty()) {
            // Copy to clipboard
            val clip = ClipData.newPlainText("text", selectedText)
            clipboardManager.setPrimaryClip(clip)
            
            // Remove selected text
            val start = editText.selectionStart.coerceAtLeast(0)
            val end = editText.selectionEnd.coerceAtMost(editText.text.length)
            editText.text.replace(start, end, "")
            
            updateTextCounts(editText.text.toString())
            updateCursorPosition()
            Toast.makeText(this, "Text cut to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            // Enable/disable undo/redo based on availability
            it.findItem(R.id.action_undo)?.isEnabled = canUndo()
            it.findItem(R.id.action_redo)?.isEnabled = canRedo()
            
            // Enable/disable copy/cut based on text selection
            val hasSelection = editText.selectionStart != editText.selectionEnd
            it.findItem(R.id.action_copy)?.isEnabled = hasSelection
            it.findItem(R.id.action_cut)?.isEnabled = hasSelection
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_file -> {
                newFile()
                true
            }
            R.id.action_open_file -> {
                openFilePicker()
                true
            }
            R.id.action_save_file -> {
                saveFile()
                true
            }
            R.id.action_copy -> {
                copyText()
                true
            }
            R.id.action_paste -> {
                pasteText()
                true
            }
            R.id.action_cut -> {
                cutText()
                true
            }
            R.id.action_undo -> {
                undo()
                true
            }
            R.id.action_redo -> {
                redo()
                true
            }
            R.id.action_stats -> {
                showDetailedStats()
                true
            }
            R.id.action_find_replace -> {
                showFindReplaceDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // Handle keyboard shortcuts
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    redo()
                    return true
                }
                KeyEvent.KEYCODE_C -> {
                    copyText()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    pasteText()
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    cutText()
                    return true
                }
                KeyEvent.KEYCODE_F -> {
                    showFindReplaceDialog()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    private fun newFile() {
        if (editText.text.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("New File")
                .setMessage("Current content will be lost. Continue?")
                .setPositiveButton("Yes") { _, _ ->
                    editText.text.clear()
                    currentFileName = null
                    currentFileUri = null
                    currentFileExtension = null
                    // Clear text history for new file
                    textHistory.clear()
                    currentHistoryIndex = -1
                    addToHistory("")
                    updateTextCounts("")
                    updateCursorPosition()
                    updateToolbarTitle()
                    Toast.makeText(this, "New file created", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No", null)
                .show()
        } else {
            editText.text.clear()
            currentFileName = null
            currentFileUri = null
            currentFileExtension = null
            // Clear text history for new file
            textHistory.clear()
            currentHistoryIndex = -1
            addToHistory("")
            updateTextCounts("")
            updateCursorPosition()
            updateToolbarTitle()
            Toast.makeText(this, "New file created", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/plain",
                "text/x-kotlin",
                "text/x-java-source",
                "application/x-java-source",
                "text/xml",
                "application/json",
                "text/markdown",
                "text/x-markdown"
            ))
        }
        openFileLauncher.launch(intent)
    }
    
    private fun openFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    content.append(line).append("\n")
                }
                
                editText.setText(content.toString())
                currentFileUri = uri
                val fileName = getFileName(uri)
                currentFileName = fileName
                currentFileExtension = getFileExtension(fileName)
                
                // Clear text history and add new content
                textHistory.clear()
                currentHistoryIndex = -1
                addToHistory(content.toString())
                
                updateToolbarTitle()
                updateTextCounts(content.toString())
                updateCursorPosition()
                applySyntaxHighlighting(content.toString())
                
                Toast.makeText(this, "File opened: $fileName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val name = it.getString(nameIndex)
            it.close()
            name
        } ?: "Unknown File"
    }
    
    private fun getFileExtension(fileName: String): String? {
        return if (fileName.contains(".")) {
            fileName.substringAfterLast(".").lowercase()
        } else null
    }
    
    private fun getFileTypeDescription(extension: String?): String {
        return when (extension?.lowercase()) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "xml" -> "XML"
            "json" -> "JSON"
            "md", "markdown" -> "Markdown"
            "txt" -> "Text"
            else -> "Text"
        }
    }
    

    
    private fun updateToolbarTitle() {
        val fileName = currentFileName
        val title = when {
            fileName != null -> "$fileName (${getFileTypeDescription(currentFileExtension)})"
            else -> getString(R.string.app_name)
        }
        supportActionBar?.title = title
    }
    
    private fun applySyntaxHighlighting(text: String) {
        if (currentFileExtension == null) return
        
        val spannableString = SpannableString(text)
        val patterns = when (currentFileExtension) {
            "kt" -> kotlinPatterns
            "java" -> javaPatterns
            "xml" -> xmlPatterns
            "json" -> jsonPatterns
            "md", "markdown" -> markdownPatterns
            else -> return // No syntax highlighting for other file types
        }
        
        // Apply syntax highlighting for each pattern
        patterns.forEach { (type, pattern) ->
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val color = syntaxColors[type] ?: Color.BLACK
                spannableString.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // Update the text without triggering the text watcher
        editText.removeTextChangedListener(textWatcher)
        editText.setText(spannableString)
        editText.addTextChangedListener(textWatcher)
    }
    
    private fun saveFile() {
        if (editText.text.isEmpty()) {
            Toast.makeText(this, "No content to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Save File")
            .setView(R.layout.dialog_save_file)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Pre-fill the file name if we have one
        val fileNameEditText = dialog.findViewById<EditText>(R.id.et_file_name)
        fileNameEditText?.setText(currentFileName ?: "")
        
        // Set up the save button click listener
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val fileName = fileNameEditText?.text?.toString()?.trim()
            
            if (fileName.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter a file name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Update file extension if needed
            val newExtension = getFileExtension(fileName)
            if (newExtension != null) {
                currentFileExtension = newExtension
            }
            
            currentFileName = fileName
            updateToolbarTitle()
            
            // Here you would implement actual file saving logic
            Toast.makeText(this, "File saved as: $fileName", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }
    
    // Find and Replace functionality
    private fun showFindReplaceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_find_replace, null)
        
        val etFindText = dialogView.findViewById<EditText>(R.id.et_find_text)
        val etReplaceText = dialogView.findViewById<EditText>(R.id.et_replace_text)
        val cbCaseSensitive = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_case_sensitive)
        val cbWholeWord = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_whole_word)
        val btnFind = dialogView.findViewById<android.widget.Button>(R.id.btn_find)
        val btnReplace = dialogView.findViewById<android.widget.Button>(R.id.btn_replace)
        val btnReplaceAll = dialogView.findViewById<android.widget.Button>(R.id.btn_replace_all)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btn_close)
        val btnPrevious = dialogView.findViewById<android.widget.Button>(R.id.btn_previous)
        val btnNext = dialogView.findViewById<android.widget.Button>(R.id.btn_next)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tv_status)
        
        // Pre-fill with current selection if any
        val selectedText = editText.text.substring(
            editText.selectionStart.coerceAtLeast(0),
            editText.selectionEnd.coerceAtMost(editText.text.length)
        )
        if (selectedText.isNotEmpty()) {
            etFindText.setText(selectedText)
        }
        
        // Set up dialog
        findReplaceDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        findReplaceDialog?.show()
        
        // Set up button click listeners
        btnFind.setOnClickListener {
            currentFindText = etFindText.text.toString()
            currentReplaceText = etReplaceText.text.toString()
            isCaseSensitive = cbCaseSensitive.isChecked
            isWholeWord = cbWholeWord.isChecked
            
            if (currentFindText.isNotEmpty()) {
                performFind()
                updateFindReplaceStatus(tvStatus)
                // Debug log
                println("Find results: ${findResults.size} matches found")
            } else {
                tvStatus.text = getString(R.string.no_matches_found)
                findResults.clear()
                currentFindIndex = -1
            }
        }
        
        btnReplace.setOnClickListener {
            if (currentFindIndex >= 0 && currentFindIndex < findResults.size) {
                val success = performReplace(etReplaceText.text.toString())
                if (success) {
                    // Update text counts after replacement
                    updateTextCounts(editText.text.toString())
                    updateCursorPosition()
                    updateFindReplaceStatus(tvStatus)
                    println("Replace successful, remaining matches: ${findResults.size}")
                }
            } else {
                tvStatus.text = "No match selected"
            }
        }
        
        btnReplaceAll.setOnClickListener {
            currentFindText = etFindText.text.toString()
            currentReplaceText = etReplaceText.text.toString()
            isCaseSensitive = cbCaseSensitive.isChecked
            isWholeWord = cbWholeWord.isChecked
            
            if (currentFindText.isNotEmpty()) {
                val count = performReplaceAll()
                tvStatus.text = getString(R.string.replaced_occurrences, count)
                // Clear find results after replace all
                findResults.clear()
                currentFindIndex = -1
                // Update text counts after replacement
                updateTextCounts(editText.text.toString())
                updateCursorPosition()
                println("Replace All completed: $count occurrences replaced")
            } else {
                tvStatus.text = "Please enter text to find"
            }
        }
        
        btnClose.setOnClickListener {
            findReplaceDialog?.dismiss()
            findReplaceDialog = null
            clearFindResults()
        }
        
        btnPrevious.setOnClickListener {
            if (findResults.isNotEmpty()) {
                currentFindIndex = if (currentFindIndex > 0) currentFindIndex - 1 else findResults.size - 1
                highlightCurrentFind()
                updateFindReplaceStatus(tvStatus)
            }
        }
        
        btnNext.setOnClickListener {
            if (findResults.isNotEmpty()) {
                currentFindIndex = if (currentFindIndex < findResults.size - 1) currentFindIndex + 1 else 0
                highlightCurrentFind()
                updateFindReplaceStatus(tvStatus)
            }
        }
        
        // Initialize find results
        if (etFindText.text.isNotEmpty()) {
            currentFindText = etFindText.text.toString()
            performFind()
            updateFindReplaceStatus(tvStatus)
        }
    }
    
    private fun performFind() {
        val text = editText.text.toString()
        findResults.clear()
        currentFindIndex = -1
        
        if (currentFindText.isEmpty()) return
        
        if (isWholeWord) {
            // Use regex for whole word matching
            val pattern = "\\b${Pattern.quote(currentFindText)}\\b"
            val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
            val regex = Pattern.compile(pattern, flags)
            val matcher = regex.matcher(text)
            
            while (matcher.find()) {
                findResults.add(matcher.start())
            }
        } else {
            // Simple string search
            var startIndex = 0
            while (true) {
                val index = if (isCaseSensitive) {
                    text.indexOf(currentFindText, startIndex)
                } else {
                    text.lowercase().indexOf(currentFindText.lowercase(), startIndex)
                }
                
                if (index == -1) break
                findResults.add(index)
                startIndex = index + 1
            }
        }
        
        if (findResults.isNotEmpty()) {
            currentFindIndex = 0
            highlightCurrentFind()
        }
    }
    

    
    private fun highlightCurrentFind() {
        if (currentFindIndex >= 0 && currentFindIndex < findResults.size) {
            val position = findResults[currentFindIndex]
            val endPosition = position + currentFindText.length
            
            // Ensure positions are within bounds
            if (position >= 0 && endPosition <= editText.text.length) {
                editText.setSelection(position, endPosition)
                editText.requestFocus()
                println("Highlighted match at position $position")
            } else {
                println("Invalid position: $position to $endPosition, text length: ${editText.text.length}")
            }
        }
    }
    
    private fun performReplace(replaceText: String): Boolean {
        if (currentFindIndex >= 0 && currentFindIndex < findResults.size) {
            val position = findResults[currentFindIndex]
            val endPosition = position + currentFindText.length
            
            editText.text.replace(position, endPosition, replaceText)
            
            // Update find results after replacement
            val lengthDiff = replaceText.length - currentFindText.length
            for (i in currentFindIndex + 1 until findResults.size) {
                findResults[i] += lengthDiff
            }
            
            // Remove the current occurrence from results
            findResults.removeAt(currentFindIndex)
            
            // Adjust current index
            if (findResults.isNotEmpty()) {
                if (currentFindIndex >= findResults.size) {
                    currentFindIndex = findResults.size - 1
                }
                highlightCurrentFind()
            } else {
                currentFindIndex = -1
            }
            
            return true
        }
        return false
    }
    
    private fun performReplaceAll(): Int {
        val text = editText.text.toString()
        var count = 0
        var offset = 0
        
        if (currentFindText.isEmpty()) return 0
        
        // First, find all occurrences
        val occurrences = mutableListOf<Int>()
        
        if (isWholeWord) {
            val pattern = "\\b${Pattern.quote(currentFindText)}\\b"
            val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
            val regex = Pattern.compile(pattern, flags)
            val matcher = regex.matcher(text)
            
            while (matcher.find()) {
                occurrences.add(matcher.start())
            }
        } else {
            var startIndex = 0
            while (true) {
                val index = if (isCaseSensitive) {
                    text.indexOf(currentFindText, startIndex)
                } else {
                    text.lowercase().indexOf(currentFindText.lowercase(), startIndex)
                }
                
                if (index == -1) break
                occurrences.add(index)
                startIndex = index + 1
            }
        }
        
        // Replace from end to beginning to avoid index shifting issues
        for (i in occurrences.size - 1 downTo 0) {
            val position = occurrences[i] + offset
            editText.text.replace(position, position + currentFindText.length, currentReplaceText)
            offset += currentReplaceText.length - currentFindText.length
            count++
        }
        
        return count
    }
    
    private fun updateFindReplaceStatus(statusView: TextView) {
        if (findResults.isEmpty()) {
            statusView.text = getString(R.string.no_matches_found)
        } else {
            val currentMatch = currentFindIndex + 1
            statusView.text = "${getString(R.string.matches_found, findResults.size)} (${currentMatch}/${findResults.size})"
        }
    }
    
    private fun clearFindResults() {
        findResults.clear()
        currentFindIndex = -1
        currentFindText = ""
        currentReplaceText = ""
    }
}