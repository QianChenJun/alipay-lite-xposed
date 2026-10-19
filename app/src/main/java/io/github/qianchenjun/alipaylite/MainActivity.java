package io.github.qianchenjun.alipaylite;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** 模块说明页：LSPosed 里点开模块时用它确认版本与验证方式。 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StringBuilder sb = new StringBuilder();
        sb.append("支付宝极速模式（官方 API 版）\n");
        sb.append("==============================\n\n");
        sb.append("做了什么\n");
        sb.append("· 把支付宝自己的极速模式开关钉成开：\n");
        sb.append("  TaskGradeController.jisu_pattern_state_key = \"T\"\n");
        sb.append("· 调用支付宝自带的低端机 mock：\n");
        sb.append("  TaskDegradeLowDeviceUtil.mockToLowPhone()\n");
        sb.append("· 逐个钉住低端机判定方法，避免缓存不一致\n\n");
        sb.append("没做什么\n");
        sb.append("· 不伪造 Build.MODEL / 内存 / 温度\n");
        sb.append("· 不全局 hook SharedPreferences\n");
        sb.append("· 不写 antsp 等安全组件文件\n\n");
        sb.append("启用步骤\n");
        sb.append("1. LSPosed 里启用本模块\n");
        sb.append("2. 作用域已锁定为支付宝（静态作用域），无需手动勾选\n");
        sb.append("3. 强行停止支付宝后重新打开\n\n");
        sb.append("验证\n");
        sb.append("adb logcat -s AlipayLite:V\n");
        sb.append("应看到 called ... -> true 的日志\n\n");
        sb.append("版本 1.0.0（libxposed API 102）\n");

        TextView text = new TextView(this);
        text.setText(sb.toString());
        text.setTextSize(13f);
        text.setPadding(48, 48, 48, 48);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(text, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }
}
