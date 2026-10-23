package org.techarea.andaime.ime

/** One tab of the emoji panel, or one submenu of the `E menu. */
class EmojiGroup(val name: String, val items: List<String>)

/**
 * Emoji by category, Andaime's own selection, minus any the device's font
 * can't draw ([available]) so older Android versions don't show empty boxes.
 */
fun emojiGroups(available: (String) -> Boolean): List<EmojiGroup> =
    emojiSource.mapNotNull { (name, list) ->
        val items = list.split(' ').filter { it.isNotEmpty() && available(it) }
        if (items.isEmpty()) null else EmojiGroup(name, items)
    }

/** 顏文字 by mood, one per line in the source. */
val kaomojiGroups: List<EmojiGroup> by lazy {
    kaomojiSource.map { (name, list) -> EmojiGroup(name, list.lines().map { it.trim() }.filter { it.isNotEmpty() }) }
}

/** The `E menu: emoji by category, then 顏文字. */
fun emojiMenu(available: (String) -> Boolean): List<MenuItem> =
    emojiGroups(available).map { it.submenu() } + MenuItem.Submenu("顏文字") { kaomojiGroups.map { it.submenu() } }

private fun EmojiGroup.submenu() = MenuItem.Submenu(name) { items.map(MenuItem::Output) }

private val emojiSource = listOf(
    "笑臉" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 " +
        "🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 " +
        "😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 " +
        "🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊",
    "手勢與人物" to "👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 " +
        "✍️ 💅 💪 👀 👅 👄 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙇 🙋 🙆 🙅 🤷 🤦 💁 🙎 🙍 💃 🕺 👫 👪 💏 💑",
    "愛心與符號" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💯 💢 💥 💫 💦 💨 💬 💭 💤 " +
        "✨ ⭐ 🌟 ⚡ 🔥 ✅ ❌ ❓ ❗ ⭕ 🆗 🆕 🆒 🆘 ♻️ ⚠️ 🚫 ➕ ➖ ➗ ✖️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪",
    "動物與自然" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 " +
        "🐞 🐢 🐍 🦖 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦒 🐪 🐑 🐐 🐈 🐕 🐇 🌵 🎄 🌲 🌳 🌴 🌱 🌿 🍀 " +
        "🍁 🍂 🍃 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌙 🌏 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ ⛄ 🌈 🌊",
    "食物" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🌽 🥕 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 " +
        "🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🍰 " +
        "🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 🍼 ☕ 🍵 🧋 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🥢 🍴",
    "活動" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🏒 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏊 🚴 🧘 🏆 🥇 🥈 🥉 🏅 🎫 🎭 🎨 " +
        "🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🧩 🎉 🎊 🎈 🎁 🎀 🧧 🎆 🎇 🎃 🎋 🎍 🎎 🎏",
    "交通與地點" to "🚗 🚕 🚙 🚌 🚎 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🚲 🛴 🚨 🚃 🚄 🚅 🚂 🚆 🚇 🚉 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ " +
        "🚤 🚢 ⚓ ⛽ 🚧 🚦 🗺️ 🗿 🗽 🗼 🏰 🏯 🎡 🎢 🎠 ⛲ 🏖️ 🏝️ ⛰️ 🗻 🌋 ⛺ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 " +
        "💒 ⛪ 🌃 🌆 🌇 🌉 🎑",
    "物品" to "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⌛ ⏳ 🔋 🔌 💡 🔦 🕯️ 💸 💵 💰 💳 💎 🔧 " +
        "🔨 🔩 ⚙️ 🔮 💊 💉 🌡️ 🔑 🚪 🛏️ 🧸 ✉️ 📩 📦 📮 📝 📁 📅 📌 📎 ✂️ 🔒 🔓 📚 📖 🔖 ✏️ 🖊️ 🔍 🔎",
)

private val kaomojiSource = listOf(
    "開心" to """
        (＾▽＾)
        (≧▽≦)
        ヽ(＾Д＾)ﾉ
        (*^▽^*)
        (｡◕‿◕｡)
        ٩(◕‿◕｡)۶
        (ﾉ◕ヮ◕)ﾉ*:･ﾟ✧
        ＼(^o^)／
        (o^▽^o)
        ヾ(≧▽≦*)o
        (๑˃̵ᴗ˂̵)و
        (＾ｖ＾)
    """,
    "喜歡" to """
        (♡˙︶˙♡)
        (´∀｀)♡
        (｡♥‿♥｡)
        (っ˘з(˘⌣˘ )
        ( ˘ ³˘)♥
        (づ￣ ³￣)づ
        (灬º‿º灬)♡
        ♡( ◡‿◡ )
    """,
    "難過" to """
        (╥﹏╥)
        (；´д｀)
        (´；ω；｀)
        (T_T)
        (ಥ﹏ಥ)
        (｡•́︿•̀｡)
        (｡ŏ﹏ŏ)
        o(╥﹏╥)o
    """,
    "生氣" to """
        (╬ Ò﹏Ó)
        (#`Д´)
        ヽ(`⌒´メ)ノ
        (╯°□°）╯︵ ┻━┻
        (ノಠ益ಠ)ノ彡┻━┻
        ┬─┬ノ( º _ ºノ)
        (￣^￣)
        ( ｀ε´ )
    """,
    "驚訝" to """
        (⊙_⊙)
        Σ(°△°|||)
        w(ﾟДﾟ)w
        (ﾟдﾟ)
        (O_O)
        ∑(O_O；)
        (・□・；)
        ヽ(°〇°)ﾉ
    """,
    "害羞" to """
        (〃▽〃)
        (*/ω＼*)
        (//▽//)
        (〃ω〃)
        (*ﾉωﾉ)
        (o〃▽〃o)
        (≧◡≦)
    """,
    "無奈" to """
        ┐(￣ヘ￣)┌
        ¯\_(ツ)_/¯
        ┐(´д｀)┌
        (￣▽￣;)
        (￣ー￣)
        (¬_¬)
        (=_=)
        (^_^;)
        ╮(╯_╰)╭
    """,
    "打招呼" to """
        (･ω･)ﾉ
        ヾ(・ω・)ノ
        (￣▽￣)ノ
        ( ´ ∀ ` )ﾉ
        (^_^)/
        (o・ω・)ﾉ
        m(_ _)m
        <(_ _)>
    """,
    "動物" to """
        (=^･ω･^=)
        ฅ^•ﻌ•^ฅ
        ʕ•ᴥ•ʔ
        (ᵔᴥᵔ)
        U・ᴥ・U
        ( ・(ｪ)・ )
        (・θ・)
        >°)))彡
    """,
    "動作" to """
        ( •_•)>⌐■-■
        (⌐■_■)
        ᕦ(ò_óˇ)ᕤ
        (ง •̀_•́)ง
        ε=ε=┌( >_<)┘
        (｡-ω-)zzz
        ( ˘▽˘)っ旦
        (っ´ω`)ﾉ(╥ω╥)
    """,
)
