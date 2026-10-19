package io.github.qianchenjun.alipaylite;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * 让支付宝走它自己的低端机降级路径。
 *
 * 使用 libxposed 现代 API（API 102），因此：
 * · 作用域由 META-INF/xposed/scope.list 声明，module.prop 里 staticScope=true 禁止用户添加作用域外的应用；
 * · 入口是 onPackageReady，时机在 AppComponentFactory 创建 Application 之前；
 * · Hook 采用拦截器链模型，返回常量即等价于替换方法返回值。
 *
 * 设计约束：
 * 1. 只调用支付宝已有的官方语义，不伪造 Build.MODEL / 内存 / 温度。
 * 2. 不全局 hook SharedPreferences。只写两个自己关心的 key。
 * 3. 每个 hook 独立 try/catch：类或方法在某个版本里消失时只记一行日志。
 * 4. 极速模式 = TaskGradeController.jisu_pattern_state_key（"T" 开 / "F" 关）。
 */
public final class MainHook extends XposedModule {

    private static final String TAG = "AlipayLite";
    private static final String TARGET_PKG = "com.eg.android.AlipayGphone";

    private static final String CLS_GRADE_CONTROLLER =
            "com.alipay.mobile.framework.degrade.TaskGradeController";
    private static final String CLS_LOW_DEVICE =
            "com.alipay.mobile.framework.degrade.TaskDegradeLowDeviceUtil";
    private static final String CLS_NEW_DEGRADE =
            "com.alipay.mobile.framework.degrade.down.NewDegradeController";
    private static final String CLS_SP_UTIL =
            "com.alipay.mobile.quinox.utils.SharedPreferenceUtil";

    /** TaskGradeController.JISU_PATTERN_STATE_KEY */
    private static final String KEY_JISU = "jisu_pattern_state_key";
    /** TaskDegradeLowDeviceUtil.mockToLowPhone 写入的标记。 */
    private static final String KEY_MOCK = "task_grade_device_mock";
    /** HomeStateConfig.TAG_FLAG */
    private static final String FLAG_ON = "T";

    private final AtomicBoolean installed = new AtomicBoolean();
    private volatile boolean mockApplied;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) {
            return;
        }
        if (!installed.compareAndSet(false, true)) {
            return;
        }
        ClassLoader classLoader = param.getClassLoader();
        String processName = currentProcessName();
        log(Log.INFO, TAG, "package ready pid=" + android.os.Process.myPid() + " process=" + processName);

        installMethodHooks(classLoader);

        // 持久化标记只在主进程写：子进程同时写同一份 SP 没有意义，还会增加启动期 IO。
        if (TARGET_PKG.equals(processName)) {
            installStartupHooks();
        }
    }

    private void installMethodHooks(ClassLoader classLoader) {
        // 官方极速模式：控制器初始化时读一次，之后以字段形式被各处读取。
        // 返回类型必须用 Boolean.TYPE：反射里 boolean 方法的 getReturnType() 是基本类型。
        hookConstant(classLoader, CLS_GRADE_CONTROLLER, "getJiSuPatternValue", Boolean.TYPE, Boolean.TRUE);
        hookConstant(classLoader, CLS_GRADE_CONTROLLER, "isJiSuPatternSwitchOpen", Boolean.TYPE, Boolean.TRUE);
        keepJiSuSwitchOn(classLoader);

        // 低端机判定在不同链路各自缓存，逐个钉住；只改“是不是低端机”，不碰任何硬件读数。
        hookConstant(classLoader, CLS_LOW_DEVICE, "isLowDevice", Boolean.TYPE, Boolean.TRUE);
        hookConstant(classLoader, CLS_LOW_DEVICE, "getDeviceGradeInfo", String.class, FLAG_ON);
        hookConstant(classLoader, CLS_LOW_DEVICE, "getDeviceScore", Integer.TYPE, Integer.valueOf(0));
        hookConstant(classLoader, CLS_NEW_DEGRADE, "isLowDevice", Boolean.TYPE, Boolean.TRUE);
        hookConstant(classLoader, CLS_NEW_DEGRADE, "readLowDeviceFlag", Boolean.TYPE, Boolean.TRUE);
    }

    /**
     * 把无参、返回类型匹配的方法整体替换成常量。
     * 遍历 declaredMethods 而不是按精确签名查找：版本升级后方法消失时只跳过，不会把模块拖挂。
     */
    private void hookConstant(ClassLoader classLoader,
                              String className,
                              String methodName,
                              Class<?> returnType,
                              Object value) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            int hooked = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    continue;
                }
                if (!returnType.equals(method.getReturnType())) {
                    continue;
                }
                final AtomicBoolean logged = new AtomicBoolean();
                hook(method).setId(className + "#" + methodName).intercept(chain -> {
                    // 第一次被调用时记一行：这是“支付宝真的查询了这个开关”的直接证据。
                    if (logged.compareAndSet(false, true)) {
                        log(Log.INFO, TAG, "called " + className + "." + methodName + " -> " + value);
                    }
                    return value;
                });
                hooked++;
            }
            log(Log.INFO, TAG, hooked > 0
                    ? "hooked " + className + "." + methodName + " x" + hooked
                    : "method missing: " + className + "." + methodName);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed " + className + "." + methodName + ": " + t);
        }
    }

    /** 支付宝自己可能把开关改回 "F"（服务端下发/低电量清理），这里把入参钉成 true。 */
    private void keepJiSuSwitchOn(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(CLS_GRADE_CONTROLLER, false, classLoader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"updateJiSuPatternSwitch".equals(method.getName())) {
                    continue;
                }
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != Boolean.TYPE) {
                    continue;
                }
                final AtomicBoolean logged = new AtomicBoolean();
                hook(method).setId(CLS_GRADE_CONTROLLER + "#updateJiSuPatternSwitch").intercept(chain -> {
                    Object original = chain.getArg(0);
                    if (logged.compareAndSet(false, true)) {
                        log(Log.INFO, TAG, "updateJiSuPatternSwitch(" + original + ") -> forced true");
                    }
                    Object[] args = chain.getArgs().toArray();
                    args[0] = Boolean.TRUE;
                    return chain.proceed(args);
                });
                log(Log.INFO, TAG, "hooked TaskGradeController.updateJiSuPatternSwitch");
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "keepJiSuSwitchOn failed: " + t);
        }
    }

    private void installStartupHooks() {
        // attachBaseContext 声明在 ContextWrapper，不在 Application 上。
        // 用 instanceof Application 过滤，只对支付宝的 Application 生效。
        try {
            Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attach).setId("ContextWrapper#attachBaseContext").intercept(chain -> {
                // ContextWrapper 每个实例都会走这里；先判类型再 proceed，避免给非 Application 的包装类加无谓开销。
                if (!(chain.getThisObject() instanceof Application)) {
                    return chain.proceed();
                }
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (arg instanceof Context) {
                    applyOfficialMock((Context) arg);
                }
                return result;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook ContextWrapper.attachBaseContext failed: " + t);
        }

        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            hook(onCreate).setId("Application#onCreate").intercept(chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                if (self instanceof Context) {
                    applyOfficialMock((Context) self);
                }
                return result;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook Application.onCreate failed: " + t);
        }
    }

    /**
     * 只做两件持久化动作：
     * · 把官方极速模式 key 写成 "T"；
     * · 调支付宝自带的 TaskDegradeLowDeviceUtil.mockToLowPhone，一次写齐 device score/scene/uvRate/playerScore。
     */
    private void applyOfficialMock(Context context) {
        if (mockApplied) {
            return;
        }
        synchronized (this) {
            if (mockApplied) {
                return;
            }
            try {
                Context appContext = context.getApplicationContext() != null
                        ? context.getApplicationContext()
                        : context;
                ClassLoader classLoader = appContext.getClassLoader();

                Class<?> spUtilClass = Class.forName(CLS_SP_UTIL, false, classLoader);
                Object spUtil = spUtilClass.getMethod("getInstance").invoke(null);
                SharedPreferences prefs = (SharedPreferences) spUtilClass
                        .getMethod("getDefaultSharedPreference", Context.class)
                        .invoke(spUtil, appContext);
                if (prefs == null) {
                    log(Log.WARN, TAG, "default SharedPreferences null, retry on onCreate");
                    return;
                }

                if (!FLAG_ON.equals(prefs.getString(KEY_JISU, null))) {
                    // apply() 同步更新内存缓存、异步落盘，启动路径不阻塞主线程。
                    prefs.edit().putString(KEY_JISU, FLAG_ON).apply();
                    log(Log.INFO, TAG, "pin " + KEY_JISU + "=T");
                }

                if (!"1".equals(prefs.getString(KEY_MOCK, null))) {
                    Class<?> lowDeviceClass = Class.forName(CLS_LOW_DEVICE, false, classLoader);
                    lowDeviceClass.getMethod("mockToLowPhone", SharedPreferences.class)
                            .invoke(null, prefs);
                    log(Log.INFO, TAG, "applied TaskDegradeLowDeviceUtil.mockToLowPhone");
                }

                mockApplied = true;
                log(Log.INFO, TAG, "startup apply done");
            } catch (Throwable t) {
                // 启动早期 SP 可能还没就绪，留给 onCreate 再试一次。
                log(Log.WARN, TAG, "applyOfficialMock failed (retry on onCreate): " + t);
            }
        }
    }

    private static String currentProcessName() {
        // Application.getProcessName() 是 API 28+；minSdk 26 用 /proc/self/cmdline 兜底。
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
