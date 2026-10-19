#!/system/bin/sh
# 还原支付宝的低端机标记（需要 root 终端执行）。
#
# 只关 LSPosed 模块不会撤销 mockToLowPhone 已经写进默认 SP 的值，
# 这个脚本负责把持久化状态改回普通设备。
#
# 用法（设备上 root 终端）：
#   sh restore_lowend.sh
# 或从 PC：
#   adb push tools/restore_lowend.sh /data/local/tmp/
#   adb shell su -c "sh /data/local/tmp/restore_lowend.sh"

PKG="com.eg.android.AlipayGphone"
SP_NAME="com.eg.android.AlipayGphone_preferences.xml"
STAMP=$(date +%s)

echo "== force-stop $PKG (all users) =="
for uid in 0 999 10; do
    am force-stop --user "$uid" "$PKG" 2>/dev/null
done

for uid in 0 999 10; do
    if [ "$uid" = "0" ]; then
        dir="/data/data/$PKG/shared_prefs"
    else
        dir="/data/user/$uid/$PKG/shared_prefs"
    fi
    file="$dir/$SP_NAME"

    if [ ! -f "$file" ]; then
        echo "skip user $uid: $file not found"
        continue
    fi

    cp -f "$file" "$file.bak.$STAMP"

    # 极速模式改回关；低端机标记改回 F；删掉 mock 及其派生值。
    sed -i \
        -e 's#<string name="jisu_pattern_state_key">T</string>#<string name="jisu_pattern_state_key">F</string>#' \
        -e 's#<string name="task_grade_device_low">T</string>#<string name="task_grade_device_low">F</string>#' \
        -e '/name="task_grade_device_mock"/d' \
        -e '/name="task_grade_device_uv_rate"/d' \
        -e '/name="task_grade_device_player_score"/d' \
        "$file"

    # score 归位到默认 100（mockToLowPhone 写的是 0）。
    sed -i 's#<int name="task_grade_device_score" value="0" />#<int name="task_grade_device_score" value="100" />#' "$file"

    echo "restored user $uid: $file (backup: $file.bak.$STAMP)"
done

echo "== done =="
echo "现在去 LSPosed 里关掉「支付宝极速模式」模块，再重新打开支付宝。"
