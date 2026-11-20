package com.example.model

enum class Rarity(
    val nameRef: String,
    val defaultDisplayName: String,
    val shopCostGold: Int
) {
    COMMON("Common", "Thường", 75),
    RARE("Rare", "Hiếm", 125),
    EPIC("Epic", "Sử Thi", 200),
    LEGENDARY("Legendary", "Huyền Thoại", 300);

    val displayName: String
        get() = LocalizationRepository.rarityName(nameRef, defaultDisplayName)
}

enum class Region(
    val nameRef: String,
    val defaultDisplayName: String
) {
    PILTOVER_ZAUN("PiltoverZaun", "Piltover & Zaun"),
    DEMACIA("Demacia", "Demacia"),
    IONIA("Ionia", "Ionia"),
    FRELJORD("Freljord", "Freljord"),
    SHADOW_ISLES("ShadowIsles", "Quần Đảo Bóng Đêm"),
    TARGON("Targon", "Targon"),
    NOXUS("Noxus", "Noxus"),
    RUNETERRA("Runeterra", "Runeterra");

    val displayName: String
        get() = LocalizationRepository.regionName(nameRef, defaultDisplayName)
}

enum class CardType(val displayName: String) {
    UNIT("Tùy Tùng"),
    CHAMPION("Tướng"),
    SPELL("Bài Phép"),
    EQUIPMENT("Trang Bị"),
    LANDMARK("Địa Danh"),
    CURSE("Lời Nguyền")
}

enum class SpellSpeed(
    val nameRef: String,
    val defaultDisplayName: String,
    val defaultDescription: String,
    val shortBadge: String
) {
    BURST(
        "Burst",
        "Siêu Tốc",
        "Có thể sử dụng bất cứ khi nào bạn được hành động. Xảy ra ngay lập tức và cho phép bạn tiếp tục sử dụng các lá bài khác.",
        "SIÊU"
    ),
    FOCUS(
        "Focus",
        "Tập Trung",
        "Có thể sử dụng ngoài giao tranh hoặc khi không có bài phép hay kỹ năng nào đang chờ xử lý. Xảy ra ngay lập tức và cho phép bạn tiếp tục sử dụng các lá bài khác.",
        "TẬP"
    ),
    FAST(
        "Fast",
        "Nhanh",
        "Có thể sử dụng bất cứ khi nào bạn được hành động. Xảy ra sau khi đối thủ có cơ hội phản ứng lại.",
        "NHANH"
    ),
    SLOW(
        "Slow",
        "Chậm",
        "Có thể sử dụng ngoài giao tranh khi không có bài phép hay kỹ năng nào đang chờ xử lý. Xảy ra sau khi đối thủ có cơ hội phản ứng lại.",
        "CHẬM"
    ),
    SKILL(
        "Skill",
        "Kỹ Năng",
        "Hiệu ứng dạng phép của một đơn vị cho phép đối phương phản ứng lại.",
        "KỸ"
    ),
    NONE(
        "None",
        "Bài Quân",
        "Triệu hồi trực tiếp lên bàn đấu.",
        "QUÂN"
    );

    val displayName: String
        get() = if (this == NONE) defaultDisplayName
        else LocalizationRepository.spellSpeedName(nameRef, defaultDisplayName)

    val description: String
        get() = if (this == NONE) defaultDescription
        else LocalizationRepository.spellSpeedDescription(nameRef, defaultDescription)
}

enum class KeywordCategory {
    COMBAT_POSITIVE,
    NEGATIVE_STATUS,
    CARD_PROPERTY
}

enum class Keyword(
    val nameRef: String,
    val defaultDisplayName: String,
    val category: KeywordCategory,
    val isSharablePositive: Boolean,
    val isStackable: Boolean,
    val defaultDescription: String
) {
    // 8.1 Từ khóa giao tranh & hiệu ứng có lợi
    OVERWHELM(
        "Overwhelm",
        "Áp Đảo", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Sát thương dư thừa ta gây ra cho kẻ chặn đòn sẽ giáng lên Nhà Chính của đối phương."
    ),
    QUICK_ATTACK(
        "QuickStrike",
        "Tấn Công Nhanh", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công, ra đòn trước kẻ chặn đòn."
    ),
    DOUBLE_ATTACK(
        "DoubleStrike",
        "Tấn Công Kép", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công, ra đòn cả trước LẪN cùng lúc với kẻ chặn đòn."
    ),
    ELUSIVE(
        "Elusive",
        "Bóng Ma", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi một đơn vị có Bóng Ma."
    ),
    SHARPSIGHT(
        "Sharpsight",
        "Tinh Mắt", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Có thể chặn các đơn vị Bóng Ma."
    ),
    FEARSOME(
        "Fearsome",
        "Đe Dọa", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi kẻ địch có từ 3 Sức Mạnh trở lên."
    ),
    BRASH(
        "Brash",
        "Ngạo Mạn", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi kẻ địch có từ 3 Máu trở lên."
    ),
    CHALLENGER(
        "Challenger",
        "Thách Đấu", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Có thể chọn đơn vị địch nào sẽ chặn đòn."
    ),
    IMPACT(
        "Impact",
        "Xung Kích", KeywordCategory.COMBAT_POSITIVE, true, true,
        "Khi đơn vị này ra đòn trong lúc tấn công, nó gây 1 sát thương lên Nhà Chính của đối phương. Từ khóa này có thể cộng dồn."
    ),
    LIFESTEAL(
        "Lifesteal",
        "Hút Máu", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Sát thương đơn vị này gây ra sẽ hồi lại cho Nhà Chính của nó lượng máu tương ứng."
    ),
    REGENERATION(
        "Regeneration",
        "Hồi Phục", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Hồi đầy máu vào cuối mỗi vòng đấu."
    ),
    TOUGH(
        "Tough",
        "Kiên Cường", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Nhận ít hơn 1 sát thương từ mọi nguồn."
    ),
    BARRIER(
        "Barrier",
        "Lá Chắn", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chặn đứng sát thương kế tiếp mà đơn vị này phải nhận. Tồn tại trong một vòng đấu."
    ),
    SPELLSHIELD(
        "SpellShield",
        "Khiên Phép", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chặn đứng bài phép hoặc kỹ năng kế tiếp của đối phương tác động lên ta."
    ),
    FURY(
        "Fury",
        "Cuồng Nộ", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi ta hạ gục một đơn vị, ban cho ta +1|+1."
    ),
    SCOUT(
        "Scout",
        "Trinh Sát", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Lần đầu tiên chỉ có các đơn vị Trinh Sát tấn công trong mỗi vòng đấu, làm mới biểu tượng tấn công của bạn."
    ),
    LURK(
        "Lurker",
        "Rình Rập", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi bạn tấn công trong lúc ta nằm trên đỉnh bộ bài của bạn, ta Rình Rập, ban cho các đồng minh Rình Rập ở mọi nơi +1|+0. Tối đa một lần mỗi vòng đấu."
    ),
    EVOLVE(
        "Evolve",
        "Tiến Hóa", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Ta nhận +2|+2 một khi bạn đã có các đơn vị sở hữu từ 6+ từ khóa tích cực khác nhau trên bàn đấu trong trận này."
    ),
    AUGMENT(
        "Augment",
        "Nâng Cấp", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi bạn sử dụng một lá bài được tạo ra, ban cho ta +1|+0."
    ),
    FATED(
        "Fated",
        "Định Mệnh", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Mỗi vòng đấu, lần đầu tiên một lá bài đồng minh chọn ta làm mục tiêu, ban cho ta +1|+1."
    ),
    FORMIDABLE(
        "Formidable",
        "Kiên Định", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Ta ra đòn bằng Máu thay vì Sức Mạnh của mình."
    ),
    HALLOWED(
        "Hallowed",
        "Thánh Hóa", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Sau khi ta bị hạ gục, trong phần còn lại của trận đấu khi đồng minh tấn công, thánh hóa kẻ tấn công đầu tiên của bạn để ban cho nó +1|+0 trong vòng đấu đó."
    ),
    SPIRIT(
        "Spirit",
        "Tinh Linh", KeywordCategory.COMBAT_POSITIVE, true, true,
        "Đơn vị này có +1|+1. Từ khóa này có thể cộng dồn."
    ),
    DEATHLESS(
        "Deathless",
        "Bất Tử", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Lần kế tiếp đơn vị này bị hạ gục, loại bỏ Bất Tử và hồi sinh nó trong trạng thái Bị Choáng với 1 Máu."
    ),
    DEEP(
        "Deep",
        "Biển Sâu", KeywordCategory.COMBAT_POSITIVE, false, false,
        "Khi bộ bài còn 15 lá trở xuống, nhận +3|+3."
    ),

    // 8.2 Từ khóa bất lợi & khống chế
    EPHEMERAL(
        "Ephemeral",
        "Phù Du", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Đơn vị này sẽ bị hạ gục khi ra đòn hoặc khi vòng đấu kết thúc."
    ),
    VULNERABLE(
        "Vulnerable",
        "Dễ Tổn Thương", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Kẻ địch có thể khiêu khích đơn vị này, ép buộc nó phải chặn đòn."
    ),
    STUN(
        "Stun",
        "Choáng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Loại bỏ một đơn vị khỏi giao tranh. Nó không thể tấn công hoặc chặn đòn trong phần còn lại của vòng đấu."
    ),
    FROSTBITE(
        "Frostbite",
        "Đóng Băng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Đặt Sức Mạnh của một đơn vị về 0 trong vòng đấu này. Chỉ số này có thể thay đổi sau đó."
    ),
    SILENCE(
        "Silenced",
        "Bị Câm Lặng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Loại bỏ toàn bộ văn bản kỹ năng và từ khóa khỏi một đơn vị."
    ),
    GLOOM(
        "Gloom",
        "U Ám", KeywordCategory.NEGATIVE_STATUS, false, true,
        "Đơn vị này có -1|-1. Từ khóa này có thể cộng dồn."
    ),
    CANT_BLOCK(
        "CantBlock",
        "Không Thể Chặn Đòn", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Bài quân này không thể tham gia chặn đòn."
    ),
    IMMOBILE(
        "Immobile",
        "Bất Động", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Không thể tấn công hoặc chặn đòn."
    ),
    SHACKLED(
        "Curse",
        "Xiềng Xích", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Khóa chặt bài quân cho đến khi lá Lời Nguyền Xiềng Xích được giải trừ từ trên tay."
    ),

    // 8.3 Thuộc tính lá bài
    ATTUNE(
        "Attune",
        "Hòa Hợp", KeywordCategory.CARD_PROPERTY, false, false,
        "Khi được triệu hồi, hồi lại 1 năng lượng phép."
    ),
    FLEETING(
        "Fleeting",
        "Phù Du", KeywordCategory.CARD_PROPERTY, false, false,
        "Các lá bài Phù Du sẽ bị loại bỏ khỏi tay khi vòng đấu kết thúc."
    ),
    ELEMENTAL(
        "ElementalSkill",
        "Kỹ Năng Nguyên Tố", KeywordCategory.CARD_PROPERTY, false, false,
        "Hiệu ứng dạng phép của một đơn vị cho phép đối phương phản ứng lại. Nó mang thuộc tính nguyên tố!"
    ),
    DAYBREAK(
        "Daybreak",
        "Hừng Đông", KeywordCategory.CARD_PROPERTY, false, false,
        "Nhận hiệu ứng cộng thêm nếu đây là lá bài ĐẦU TIÊN bạn sử dụng trong vòng đấu."
    ),
    SUPPORT(
        "Support",
        "Hỗ Trợ", KeywordCategory.CARD_PROPERTY, false, false,
        "Tấn công bằng một đơn vị Hỗ Trợ sẽ cường hóa cho đồng minh đứng ngay bên phải nó."
    );

    val displayName: String
        get() = if (this == SHACKLED) defaultDisplayName
        else LocalizationRepository.keywordName(nameRef, defaultDisplayName)

    val description: String
        get() = if (this == SHACKLED) defaultDescription
        else LocalizationRepository.keywordDescription(nameRef, defaultDescription)

    companion object {
        val sharablePool: List<Keyword> by lazy {
            entries.filter { it.isSharablePositive && it != EVOLVE }
        }

        fun findByNameRef(nameRef: String): Keyword? =
            entries.firstOrNull { it.nameRef.equals(nameRef, ignoreCase = true) }
    }
}

enum class TriggerHook(
    val nameRef: String,
    val defaultDisplayName: String
) {
    PLAY("Play", "Xuất Trận"),
    SUMMON("Summon", "Triệu Hồi"),
    STRIKE("Strike", "Ra Đòn"),
    NEXUS_STRIKE("NexusStrike", "Đánh Nhà Chính"),
    ATTACK("Attack", "Tấn Công"),
    SUPPORT("Support", "Hỗ Trợ"),
    LAST_BREATH("LastBreath", "Di Nguyện"),
    ROUND_START("RoundStart", "Bắt Đầu Vòng Đấu"),
    ROUND_END("RoundEnd", "Kết Thúc Vòng Đấu"),
    LEVEL_UP("LevelUp", "Thăng Cấp");

    val displayName: String
        get() = LocalizationRepository.termName(nameRef, defaultDisplayName)
}

enum class StateCondition(
    val nameRef: String,
    val defaultDisplayName: String,
    val defaultDescription: String
) {
    SLAY("Slay", "Hạ Gục", "Khi bạn tiêu diệt một đơn vị thông qua sát thương, hiệu ứng tiêu diệt, hoặc dùng đồng minh ra đòn hạ gục nó."),
    PLUNDER("Plunder", "Cướp Bóc", "Lá bài kích hoạt kỹ năng Cướp Bóc khi được đánh ra nếu bạn đã gây sát thương lên Nhà Chính đối phương trong vòng đấu này."),
    REPUTATION("Reputation", "Danh Tiếng", "Kích hoạt nếu các đồng minh đã ra đòn gây từ 5+ sát thương ít nhất 4 lần trong trận đấu này."),
    FLOW("Flow", "Dòng Chảy", "Lá bài kích hoạt Dòng Chảy khi Bắt Đầu Vòng Đấu nếu bạn đã dùng 2+ bài phép hoặc kỹ năng ở vòng trước."),
    DAYBREAK("Daybreak", "Hừng Đông", "Nhận hiệu ứng cộng thêm nếu đây là lá bài ĐẦU TIÊN bạn sử dụng trong vòng đấu."),
    NIGHTFALL("Nightfall", "Màn Đêm", "Nhận hiệu ứng cộng thêm nếu đây KHÔNG phải lá bài đầu tiên bạn sử dụng trong vòng đấu."),
    ENLIGHTENED("Enlightened", "Khai Sáng", "Bạn đạt trạng thái Khai Sáng khi có đủ 10 viên ngọc năng lượng tối đa."),
    DEEP("Deep", "Biển Sâu", "Kích hoạt khi bộ bài còn 15 lá trở xuống.");

    val displayName: String
        get() = LocalizationRepository.termName(nameRef, defaultDisplayName)

    val description: String
        get() = LocalizationRepository.termDescription(nameRef, defaultDescription)
}
