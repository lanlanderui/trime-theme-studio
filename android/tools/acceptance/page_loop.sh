#!/system/bin/sh
# 真机稳定性验收（A7）：九个页面各进出一次算一轮，默认 50 轮。
#
# 用法：
#   adb push tools/acceptance/page_loop.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/page_loop.sh 50
#
# 用 am start 而不是点击：脚本化的点击和页面加载会互相追尾（BACK 落在还没切完的
# 页面上），上一版因此「跑完 50 轮但页面根本没开」。调试包用
# app/src/debug/AndroidManifest.xml 把这些页面导出，只为让这条验收能无人值守地跑。
#
# 每页的等待不一样：诊断页和布局编辑页要读整份主题（200KB）+ 展开，0.6s 不够，
# 会误报 MISS。判据只有一条：最后一行 `done misses=0`。

PKG=com.trime.editor.debug
CLS=com.trime.editor
THEME=tongwenfeng.trime
N=${1:-10}
misses=0

visit() {
    wait_s=$1
    act=$2
    shift 2
    # NEW_TASK|CLEAR_TOP：不加这两个，am start 会把意图交给任务顶上的那一页
    # （「Activity not started, intent has been delivered to currently running
    # top-most instance」），后面的轮次其实一直在看同一个页面。
    am start -f 0x14000000 -n $PKG/$CLS.$act "$@" >/dev/null 2>&1
    sleep $wait_s
    f=$(dumpsys window | grep -m1 mCurrentFocus)
    case "$f" in
        *"$act"*) input keyevent KEYCODE_BACK; sleep 0.3 ;;
        *)
            echo "MISS expected=$act actual=$f"
            misses=$((misses + 1))
            ;;
    esac
}

i=1
while [ $i -le $N ]; do
    T="--es theme_id $THEME --es theme_name $THEME"
    visit 1.0 ThemePreviewActivity $T
    visit 0.9 StyleEditorActivity $T
    visit 0.9 ColorEditorActivity $T
    visit 0.9 KeyboardListActivity $T
    visit 1.4 KeyboardEditorActivity $T --es layout_id letter --es layout_name letter
    visit 0.9 PresetKeyListActivity $T
    visit 0.9 ToolBarActivity $T
    visit 0.9 CandidateWindowActivity $T
    visit 2.0 DiagnosticsActivity $T
    echo "round $i misses=$misses"
    i=$((i + 1))
done
echo "done misses=$misses"
