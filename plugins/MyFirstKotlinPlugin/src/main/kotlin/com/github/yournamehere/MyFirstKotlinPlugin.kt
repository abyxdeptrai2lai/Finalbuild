package com.github.yourusernamehere

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.lytefast.flexinput.fragment.FlexInputFragment
import kotlin.concurrent.thread
import kotlin.random.Random

@AliucordPlugin
class PasteAndSpamPlugin : Plugin() {
    @Volatile private var isSpamming = false
    private var spamThread: Thread? = null
    private var customPrefix = ""
    private var isPrefixEnabled = false
    private var isLoopEnabled = false

    @SuppressLint("ClickableViewAccessibility")
    override fun start(context: Context) {
        patcher.after<FlexInputFragment>("onViewCreated", View::class.java) { param ->
            val view = param.args[0] as View
            val input = view.findViewById<EditText>(com.discord.R.id.chat_input_edit_text) ?: return@after
            
            // Tìm thanh công cụ chứa nút + và khung chat
            val parent = input.parent as? ViewGroup ?: return@after

            // Nút Paste (nằm ngay sau dấu +)
            val pasteBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(15, 15, 15, 15)
                setOnClickListener {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (cb.hasPrimaryClip()) {
                        val text = cb.primaryClip?.getItemAt(0)?.text
                        if (text != null) {
                            val start = input.selectionStart.coerceAtLeast(0)
                            input.text.insert(start, text)
                        }
                    }
                }
            }
            
            // Nút Spam (hình vuông, nằm kế nút Paste)
            val spamBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_sort_by_size) // Icon vuông
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(15, 15, 15, 15)
                setOnClickListener { showSpamMenu(context, input) }
            }

            // Chèn vào sau dấu + (vị trí index 1 và 2)
            try {
                parent.addView(pasteBtn, 1)
                parent.addView(spamBtn, 2)
            } catch (e: Exception) {
                parent.addView(pasteBtn)
                parent.addView(spamBtn)
            }

            // Tự động chèn Prefix khi khung chat trống (sau khi gửi)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isPrefixEnabled && customPrefix.isNotEmpty() && s != null && s.toString().trim().isEmpty() && !isSpamming) {
                        // Nếu khung chat trống và không phải đang trong quá trình spam
                        input.postDelayed({
                            if (input.text.toString().trim().isEmpty()) {
                                input.setText("$customPrefix ")
                                input.setSelection(input.text.length)
                            }
                        }, 100)
                    }
                }
            })
        }
    }

    override fun stop(context: Context) {
        isSpamming = false
        spamThread?.interrupt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSpamMenu(context: Context, chatInput: EditText) {
        val dialog = AlertDialog.Builder(context).create()
        dialog.setTitle("Cấu hình Auto Spam")

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
        }

        // Thanh kéo di chuyển menu
        val dragBar = TextView(context).apply {
            text = "≡ Kéo để di chuyển ≡"
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.LTGRAY)
            setPadding(0, 25, 0, 25)
        }
        
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        scrollView.addView(layout)

        // Dòng 1: Delay
        val delayInput = EditText(context).apply {
            hint = "1. Delay (ms) - vd: 1000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(delayInput)

        // Dòng 2: Tag
        val tagLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val tagCheck = CheckBox(context)
        val tagInput = EditText(context).apply { hint = "2. ID cần tag (vd: 123456)" }
        tagLayout.addView(tagCheck)
        tagLayout.addView(tagInput)
        layout.addView(tagLayout)

        // Dòng 2.5: Tùy chọn vị trí Tag
        layout.addView(TextView(context).apply { text = "2.5 Vị trí Tag:" })
        val tagPosGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val tagPosOptions = arrayOf("Đầu", "Giữa", "Cuối", "Random", "Cả 2", "Cả 3")
        for (i in tagPosOptions.indices) {
            val rb = RadioButton(context).apply { text = tagPosOptions[i]; id = i }
            tagPosGroup.addView(rb)
        }
        tagPosGroup.check(0)
        layout.addView(tagPosGroup)

        // Dòng 3: Vị trí gửi chữ
        layout.addView(TextView(context).apply { text = "3. Vị trí gửi chữ:" })
        val msgPosGroup = RadioGroup(context)
        val msgPosOptions = arrayOf("Đầu - Cuối", "Cuối - Đầu", "Random", "Full (CopyPaste)")
        for (i in msgPosOptions.indices) {
            val rb = RadioButton(context).apply { text = msgPosOptions[i]; id = i }
            msgPosGroup.addView(rb)
        }
        msgPosGroup.check(0)
        layout.addView(msgPosGroup)

        // Dòng 5: Nguồn chữ
        layout.addView(TextView(context).apply { text = "5. Nguồn chữ:" })
        val msgSourceLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val fileBtn = Button(context).apply { text = "Nhập file .txt" }
        val manualBtn = Button(context).apply { text = "Nhập tay" }
        msgSourceLayout.addView(fileBtn)
        msgSourceLayout.addView(manualBtn)
        layout.addView(msgSourceLayout)
        
        val msgList = mutableListOf<String>()
        
        // Nhập file (giả lập đọc từ bộ nhớ)
        fileBtn.setOnClickListener {
            val pathDialog = AlertDialog.Builder(context)
            pathDialog.setTitle("Nhập đường dẫn file .txt")
            val pathInput = EditText(context)
            pathInput.setText("/sdcard/spam.txt")
            pathDialog.setView(pathInput)
            pathDialog.setPositiveButton("Đọc") { _, _ ->
                try {
                    val file = java.io.File(pathInput.text.toString())
                    if (file.exists()) {
                        msgList.clear()
                        msgList.addAll(file.readLines().filter { it.isNotBlank() })
                        Toast.makeText(context, "Đã tải ${msgList.size} dòng", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "File không tồn tại!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi đọc file!", Toast.LENGTH_SHORT).show()
                }
            }
            pathDialog.show()
        }
        
        // Nhập tay
        manualBtn.setOnClickListener {
            val inputDialog = AlertDialog.Builder(context)
            inputDialog.setTitle("Nhập nội dung (mỗi dòng 1 câu)")
            val editText = EditText(context)
            editText.setMinLines(5)
            inputDialog.setView(editText)
            inputDialog.setPositiveButton("Lưu") { _, _ ->
                msgList.clear()
                msgList.addAll(editText.text.toString().split("\n").filter { it.isNotBlank() })
                Toast.makeText(context, "Đã lưu ${msgList.size} dòng", Toast.LENGTH_SHORT).show()
            }
            inputDialog.show()
        }

        // Loop
        val loopCheck = CheckBox(context).apply { text = "Loop (Lặp lại khi hết)" }
        layout.addView(loopCheck)

        // Prefix
        layout.addView(TextView(context).apply { text = "Prefix (Tự chèn khi chat trống):" })
        val prefixLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val prefixBtn = Button(context).apply { text = "Nhập Prefix" }
        val prefixCheck = CheckBox(context).apply { text = "Bật" }
        prefixLayout.addView(prefixBtn)
        prefixLayout.addView(prefixCheck)
        layout.addView(prefixLayout)

        prefixBtn.setOnClickListener {
            val inputDialog = AlertDialog.Builder(context)
            inputDialog.setTitle("Nhập Prefix")
            val editText = EditText(context)
            editText.setText(customPrefix)
            inputDialog.setView(editText)
            inputDialog.setPositiveButton("Lưu") { _, _ ->
                customPrefix = editText.text.toString()
            }
            inputDialog.show()
        }

        // Start / Stop
        val btnLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = Button(context).apply { text = "START" }
        val stopBtn = Button(context).apply { text = "STOP" }
        btnLayout.addView(startBtn)
        btnLayout.addView(stopBtn)
        layout.addView(btnLayout)

        rootLayout.addView(dragBar)
        rootLayout.addView(scrollView)

        // Logic di chuyển menu
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        dragBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = dialog.window?.attributes?.x ?: 0
                    initialY = dialog.window?.attributes?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = dialog.window?.attributes
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    dialog.window?.attributes = params
                    true
                }
                else -> false
            }
        }

        // Bắt đầu Spam
        startBtn.setOnClickListener {
            if (isSpamming) return@setOnClickListener
            if (msgList.isEmpty()) {
                Toast.makeText(context, "Chưa có nội dung!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val delay = delayInput.text.toString().toLongOrNull() ?: 1000L
            val doTag = tagCheck.isChecked
            val tagId = tagInput.text.toString().trim()
            val tagPos = tagPosGroup.checkedRadioButtonId
            val msgPos = msgPosGroup.checkedRadioButtonId
            val isLoop = loopCheck.isChecked
            isPrefixEnabled = prefixCheck.isChecked

            isSpamming = true
            spamThread = thread {
                var index = 0
                while (isSpamming) {
                    var msg = if (isLoop) {
                        if (index >= msgList.size) index = 0
                        msgList[index++]
                    } else {
                        if (index >= msgList.size) break
                        msgList[index++]
                    }

                    // Xử lý vị trí chữ
                    msg = when (msgPos) {
                        0 -> "$msg\n"
                        1 -> "\n$msg"
                        2 -> if (Random.nextBoolean()) "$msg\n" else "\n$msg"
                        3 -> msg // Full (Ko khoảng trắng)
                        else -> msg
                    }

                    // Xử lý Tag & Tự động phát hiện Prefix trong câu
                    if (doTag && tagId.isNotEmpty()) {
                        val mentionId = if (tagId.startsWith("<@")) tagId else "<@$tagId>"
                        val prefixesToCheck = arrayOf("> # ", "> ", "# ")
                        var prefixFound = ""
                        var contentWithoutPrefix = msg

                        for (p in prefixesToCheck) {
                            if (msg.startsWith(p)) {
                                prefixFound = p
                                contentWithoutPrefix = msg.substring(p.length)
                                break
                            }
                        }

                        // Nếu có prefix trong câu, tag sẽ nằm ngay sau prefix đó (cách 1 khoảng trắng)
                        msg = if (prefixFound.isNotEmpty()) {
                            "$prefixFound$mentionId $contentWithoutPrefix"
                        } else {
                            // Nếu không có prefix, chèn theo vị trí Freddy chọn
                            when (tagPos) {
                                0 -> "$mentionId $msg" // Đầu
                                1 -> { // Giữa
                                    val lines = msg.split("\n")
                                    if (lines.size > 1) {
                                        val mid = lines.size / 2
                                        lines.toMutableList().apply { add(mid, mentionId) }.joinToString("\n")
                                    } else { "$mentionId $msg" }
                                }
                                2 -> "$msg$mentionId" // Cuối
                                3 -> if (Random.nextBoolean()) "$mentionId $msg" else "$msg$mentionId" // Random
                                4 -> "$mentionId\n$msg\n$mentionId" // Cả 2 đầu cuối
                                5 -> "$mentionId\n$mentionId\n$mentionId\n$msg" // Cả 3 (giả lập 3 tag)
                                else -> msg
                            }
                        }
                    }

                    val finalMsg = msg
                    (context as android.app.Activity).runOnUiThread {
                        chatInput.setText(finalMsg)
                        chatInput.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND)
                    }
                    Thread.sleep(delay)
                }
                isSpamming = false
            }
            Toast.makeText(context, "Đã bắt đầu spam!", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            isSpamming = false
            spamThread?.interrupt()
            Toast.makeText(context, "Đã dừng spam!", Toast.LENGTH_SHORT).show()
        }

        dialog.setView(rootLayout)
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}
