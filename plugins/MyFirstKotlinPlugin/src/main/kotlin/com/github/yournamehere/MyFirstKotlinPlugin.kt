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
import de.robv.android.xposed.XposedBridge
import kotlin.concurrent.thread
import kotlin.random.Random

@AliucordPlugin
class MyFirstKotlinPlugin : Plugin() {
    @Volatile private var isSpamming = false
    private var spamThread: Thread? = null
    private var customPrefix = ""
    private var isPrefixEnabled = false

    @SuppressLint("ClickableViewAccessibility")
    override fun start(context: Context) {
        try {
            val activity = context as android.app.Activity
            val rootView = activity.window.decorView as ViewGroup

            var chatInput: EditText? = null
            
            patcher.after<FlexInputFragment>("onViewCreated", View::class.java) { param ->
                val view = param.args[0] as View
                chatInput = view.findViewById(com.discord.R.id.chat_input_edit_text)
                
                // Tự động chèn Prefix khi khung chat trống
                chatInput?.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (isPrefixEnabled && customPrefix.isNotEmpty() && s != null && s.toString().trim().isEmpty() && !isSpamming) {
                            chatInput?.postDelayed({
                                if (chatInput?.text.toString().trim().isEmpty()) {
                                    chatInput?.setText("$customPrefix ")
                                    chatInput?.setSelection(chatInput?.text?.length ?: 0)
                                }
                            }, 100)
                        }
                    }
                })
            }

            // Nút Paste (Lơ lửng)
            val pasteBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(20, 20, 20, 20)
            }
            val pasteParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            pasteParams.gravity = Gravity.TOP or Gravity.START
            pasteParams.leftMargin = 100
            pasteParams.topMargin = 300

            // Nút Spam (Lơ lửng)
            val spamBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_sort_by_size)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(20, 20, 20, 20)
            }
            val spamParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            spamParams.gravity = Gravity.TOP or Gravity.START
            spamParams.leftMargin = 200
            spamParams.topMargin = 300

            rootView.addView(pasteBtn, pasteParams)
            rootView.addView(spamBtn, spamParams)

            // Logic kéo thả nút Paste
            var pInitX = 0; var pInitY = 0; var pTouchX = 0f; var pTouchY = 0f; var pMoved = false
            pasteBtn.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pInitX = pasteParams.leftMargin; pInitY = pasteParams.topMargin
                        pTouchX = event.rawX; pTouchY = event.rawY
                        pMoved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - pTouchX).toInt()
                        val dy = (event.rawY - pTouchY).toInt()
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) pMoved = true
                        pasteParams.leftMargin = pInitX + dx
                        pasteParams.topMargin = pInitY + dy
                        v.layoutParams = pasteParams
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!pMoved) {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            if (cb.hasPrimaryClip()) {
                                val text = cb.primaryClip?.getItemAt(0)?.text
                                if (text != null) {
                                    val input = chatInput
                                    if (input != null) {
                                        val start = input.selectionStart.coerceAtLeast(0)
                                        input.text.insert(start, text)
                                    } else {
                                        Toast.makeText(context, "Mở khung chat trước!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            // Logic kéo thả nút Spam
            var sInitX = 0; var sInitY = 0; var sTouchX = 0f; var sTouchY = 0f; var sMoved = false
            spamBtn.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sInitX = spamParams.leftMargin; sInitY = spamParams.topMargin
                        sTouchX = event.rawX; sTouchY = event.rawY
                        sMoved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - sTouchX).toInt()
                        val dy = (event.rawY - sTouchY).toInt()
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) sMoved = true
                        spamParams.leftMargin = sInitX + dx
                        spamParams.topMargin = sInitY + dy
                        v.layoutParams = spamParams
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!sMoved) {
                            val input = chatInput
                            if (input != null) {
                                showSpamMenu(context, input)
                            } else {
                                Toast.makeText(context, "Mở khung chat trước!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

        } catch (e: Throwable) {
            XposedBridge.log(e)
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

        val delayInput = EditText(context).apply {
            hint = "1. Delay (ms) - vd: 1000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(delayInput)

        val tagLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val tagCheck = CheckBox(context)
        val tagInput = EditText(context).apply { hint = "2. ID cần tag (vd: 123456)" }
        tagLayout.addView(tagCheck)
        tagLayout.addView(tagInput)
        layout.addView(tagLayout)

        layout.addView(TextView(context).apply { text = "2.5 Vị trí Tag:" })
        val tagPosGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val tagPosOptions = arrayOf("Đầu", "Giữa", "Cuối", "Random", "Cả 2", "Cả 3")
        for (i in tagPosOptions.indices) {
            // FIX LỖI: Thay id = i thành this.id = i + 100
            val rb = RadioButton(context).apply { text = tagPosOptions[i]; this.id = i + 100 }
            tagPosGroup.addView(rb)
        }
        tagPosGroup.check(100)
        layout.addView(tagPosGroup)

        layout.addView(TextView(context).apply { text = "3. Vị trí gửi chữ:" })
        val msgPosGroup = RadioGroup(context)
        val msgPosOptions = arrayOf("Đầu - Cuối", "Cuối - Đầu", "Random", "Full (CopyPaste)")
        for (i in msgPosOptions.indices) {
            // FIX LỖI: Thay id = i thành this.id = i + 200
            val rb = RadioButton(context).apply { text = msgPosOptions[i]; this.id = i + 200 }
            msgPosGroup.addView(rb)
        }
        msgPosGroup.check(200)
        layout.addView(msgPosGroup)

        layout.addView(TextView(context).apply { text = "5. Nguồn chữ:" })
        val msgSourceLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val fileBtn = Button(context).apply { text = "Nhập file .txt" }
        val manualBtn = Button(context).apply { text = "Nhập tay" }
        msgSourceLayout.addView(fileBtn)
        msgSourceLayout.addView(manualBtn)
        layout.addView(msgSourceLayout)
        
        val msgList = mutableListOf<String>()
        
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

        val loopCheck = CheckBox(context).apply { text = "Loop (Lặp lại khi hết)" }
        layout.addView(loopCheck)

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

        val btnLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = Button(context).apply { text = "START" }
        val stopBtn = Button(context).apply { text = "STOP" }
        btnLayout.addView(startBtn)
        btnLayout.addView(stopBtn)
        layout.addView(btnLayout)

        rootLayout.addView(dragBar)
        rootLayout.addView(scrollView)

        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
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

        startBtn.setOnClickListener {
            if (isSpamming) return@setOnClickListener
            if (msgList.isEmpty()) {
                Toast.makeText(context, "Chưa có nội dung!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val delay = delayInput.text.toString().toLongOrNull() ?: 1000L
            val doTag = tagCheck.isChecked
            val tagId = tagInput.text.toString().trim()
            val tagPos = tagPosGroup.checkedRadioButtonId - 100 // FIX LỖI: Trừ đi 100 để lấy đúng vị trí
            val msgPos = msgPosGroup.checkedRadioButtonId - 200 // FIX LỖI: Trừ đi 200 để lấy đúng vị trí
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

                    msg = when (msgPos) {
                        0 -> "$msg\n"
                        1 -> "\n$msg"
                        2 -> if (Random.nextBoolean()) "$msg\n" else "\n$msg"
                        3 -> msg
                        else -> msg
                    }

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

                        msg = if (prefixFound.isNotEmpty()) {
                            "$prefixFound$mentionId $contentWithoutPrefix"
                        } else {
                            when (tagPos) {
                                0 -> "$mentionId $msg"
                                1 -> {
                                    val lines = msg.split("\n")
                                    if (lines.size > 1) {
                                        val mid = lines.size / 2
                                        lines.toMutableList().apply { add(mid, mentionId) }.joinToString("\n")
                                    } else { "$mentionId $msg" }
                                }
                                2 -> "$msg$mentionId"
                                3 -> if (Random.nextBoolean()) "$mentionId $msg" else "$msg$mentionId"
                                4 -> "$mentionId\n$msg\n$mentionId"
                                5 -> "$mentionId\n$mentionId\n$mentionId\n$msg"
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
