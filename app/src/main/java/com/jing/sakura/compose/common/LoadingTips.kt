package com.jing.sakura.compose.common

import kotlin.random.Random

internal const val LOADING_TIP_INTERVAL_MS = 7_000L

internal data class LoadingTip(
    val text: String,
    val sourceName: String? = null,
    val sourceUrl: String? = null
)

/** Sources checked on 2026-10-18; short original paraphrases, with no network work at display time. */
internal val loadingTips: List<LoadingTip> = buildList {
    fun facts(sourceName: String, sourceUrl: String, vararg text: String) {
        text.forEach { add(LoadingTip(it, sourceName, sourceUrl)) }
    }
    facts("NASA", "https://science.nasa.gov/mercury/facts/",
        "水星繞太陽一圈只需約 88 個地球日，係八大行星中最快。",
        "水星由一次日出到下一次日出，約要等 176 個地球日。",
        "喺水星睇太陽，太陽睇落會比喺地球大三倍以上。",
        "水星表面日間可達約 430°C，夜晚可跌至約 −180°C。"
    )
    facts("NASA", "https://science.nasa.gov/venus/venus-facts/",
        "金星自轉一圈約需 243 個地球日，比公轉一圈嘅 225 日更長。",
        "金星自轉方向同地球相反，所以太陽會喺西邊升起。",
        "太陽系最熱嘅行星係金星，表面溫度約 467°C。",
        "金星冇天然衛星；水星亦一樣。"
    )
    facts("NASA", "https://science.nasa.gov/earth/facts/",
        "陽光由太陽出發，要用約 8 分鐘先到達地球。",
        "地球同月球嘅平均距離，足以排入約 30 個地球。",
        "地球近地面嘅空氣，約 78% 係氮氣、21% 係氧氣。",
        "地球嘅四季同自轉軸傾斜有關；南北半球嘅季節相反。"
    )
    facts("NASA", "https://science.nasa.gov/mars/facts/",
        "火星望落紅紅哋，係因為岩石同塵土中嘅鐵氧化咗。",
        "火星自轉一圈約需 24.6 小時，同地球好接近。",
        "火星一年約等於 687 個地球日，四季亦比地球長。",
        "火星有兩個細小衛星，形狀都似薯仔。"
    )
    facts("NASA", "https://science.nasa.gov/jupiter/jupiter-facts/",
        "木星係太陽系最大嘅行星，直徑約為地球嘅 11 倍。",
        "木星自轉一圈只需約 9.9 小時，係八大行星中最快。",
        "木星嘅大紅斑係一場巨大風暴，已經持續咗幾百年。",
        "如果地球似一粒提子咁大，木星就大約似一個籃球。"
    )
    facts("NASA", "https://science.nasa.gov/saturn/facts/",
        "土星繞太陽一圈約需 29.4 個地球年。",
        "土星自轉一圈約需 10.7 小時，一日比地球短好多。",
        "陽光去到土星，路程大約要 80 分鐘。",
        "土星並非唯一有環嘅行星，佢嘅環只係特別壯觀。"
    )
    facts("NASA", "https://science.nasa.gov/uranus/facts/",
        "天王星嘅自轉軸傾得好斜，睇落好似側住身滾動。",
        "天王星繞太陽一圈約需 84 個地球年。",
        "天王星係第一個借助望遠鏡發現嘅行星，發現於 1781 年。",
        "天王星嘅衛星，以莎士比亞同詩人蒲柏作品中嘅角色命名。"
    )
    facts("NASA", "https://science.nasa.gov/neptune/neptune-facts/",
        "海王星繞太陽一圈約需 165 個地球年。",
        "陽光由太陽去海王星，大約要行 4 個鐘。",
        "海王星係先靠數學預測位置，再喺 1846 年被觀測到嘅行星。",
        "海王星喺 2011 年，先完成發現以來第一次繞太陽公轉。"
    )
    facts("NOAA", "https://oceanservice.noaa.gov/facts/seahorse.html",
        "海馬由爸爸懷孕同生仔，媽媽會先將卵放入爸爸嘅育兒袋。",
        "海馬可以改變身體顏色，融入身邊嘅環境。"
    )
    facts("NOAA", "https://oceanservice.noaa.gov/facts/coral.html",
        "珊瑚其實係動物，會用細小觸手捕捉水中食物。",
        "好多珊瑚由成百上千隻珊瑚蟲組成，慢慢築起珊瑚礁。"
    )
    facts("NOAA", "https://oceanservice.noaa.gov/facts/oceanblue.html",
        "海水會吸收光譜中偏紅嘅光，令我哋較容易見到藍色。",
        "海水中嘅懸浮粒子會影響顏色，海洋亦可以呈現綠色或紅色。"
    )
    facts("NOAA", "https://oceanservice.noaa.gov/facts/pressure.html",
        "喺海中每下降約 10 米，水壓就會增加約一個大氣壓。"
    )
    facts("NOAA", "https://oceanservice.noaa.gov/facts/light_travel.html",
        "海面下超過約 1,000 米，陽光已經照唔到，係一片黑暗。"
    )
    listOf(
        "遙控器今日嘅運動量，可能已經超越咗主人。",
        "我只係打算睇一集。片尾曲：你頭先都係咁講。",
        "如果瞓覺都有片尾彩蛋，我應該會早啲上床。",
        "今日嘅小目標：睇完一集，再認真考慮下一集。",
        "零食袋最擅長嘅魔術：一開就慢慢消失。",
        "主角準備拯救世界，我準備搵個舒服坐姿。",
        "最難預測嘅劇情，係今晚幾點先捨得瞓。",
        "梳化嘅隱藏技能：令人自動進入休息模式。",
        "如果星期一有跳過掣，遙控器應該會非常忙。",
        "片尾曲一響，下一集嘅誘惑就開始熱身。"
    ).forEach { add(LoadingTip(it)) }
}

/** One shared shuffle bag also varies the first tip when opening another page. */
internal class LoadingTipDeck(
    private val tips: List<LoadingTip> = loadingTips,
    private val random: Random = Random.Default
) {
    private val pending = ArrayDeque<LoadingTip>()
    private var previous: LoadingTip? = null

    init {
        require(tips.isNotEmpty())
    }

    @Synchronized
    fun next(): LoadingTip {
        if (pending.isEmpty()) {
            val shuffled = tips.shuffled(random).toMutableList()
            if (shuffled.size > 1 && shuffled.first() == previous) {
                val replacement = shuffled.indexOfFirst { it != previous }
                if (replacement > 0) {
                    val first = shuffled[0]
                    shuffled[0] = shuffled[replacement]
                    shuffled[replacement] = first
                }
            }
            pending.addAll(shuffled)
        }
        return pending.removeFirst().also { previous = it }
    }
}

internal val sharedLoadingTipDeck = LoadingTipDeck()
