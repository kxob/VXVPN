import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import static de.robv.android.xposed.XposedBridge.*;
import static de.robv.android.xposed.XposedHelpers.*;

import android.app.*;
import android.content.res.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.*;
import org.luckypray.dexkit.query.enums.*;
import org.luckypray.dexkit.query.matchers.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class NԀΛ implements IXposedHookLoadPackage, IXposedHookZygoteInit {
	public static XModuleResources mRes;
	private DexKitBridge bridge; private ClassLoader cl; private Resources res;
	private Activity mainActivity; private Service vpnService; private int appCount;
	static {System.loadLibrary("dexkit");}
	public static String getString(String name){return mRes.getString(mRes.getIdentifier(name,"string","cum.wrongchao.v2vpn"));}
	private static XC_MethodHook before(Consumer<MethodHookParam>before){return new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam param){before.accept(param);}};}
	private static XC_MethodHook after(Consumer<MethodHookParam>after){return new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam param){after.accept(param);}};}
	private static XC_MethodReplacement replace(Function<MethodHookParam,Object>replace){return new XC_MethodReplacement(){@Override protected Object replaceHookedMethod(MethodHookParam param){return replace.apply(param);}};}
	private Class<?>matchClass(Consumer<ClassMatcher>matcher)throws ClassNotFoundException{ClassMatcher cls=ClassMatcher.create();matcher.accept(cls);return bridge.findClass(FindClass.create().matcher(cls)).single().getInstance(cl);}
	private Field matchField(Consumer<FieldMatcher>matcher)throws NoSuchFieldException{FieldMatcher field=FieldMatcher.create();matcher.accept(field);return bridge.findField(FindField.create().matcher(field)).single().getFieldInstance(cl);}
	private Method matchMethod(Consumer<MethodMatcher>matcher)throws NoSuchMethodException{MethodMatcher method=MethodMatcher.create();matcher.accept(method);return bridge.findMethod(FindMethod.create().matcher(method)).single().getMethodInstance(cl);}
	private int getIdentifier(String name){return getIdentifier("id", name);} private int getIdentifier(String type,String name){return res.getIdentifier(name,type,getString("targ"));}
	private View findView(String name){return findView("id",name);} private View findView(String type,String name){return mainActivity.findViewById(getIdentifier(type,name));}
	private void setText(String tv,String txt){((TextView)findView(tv)).setText(txt);} private String getText(String name){return res.getString(res.getIdentifier(name,"string",getString("targ")));}
	@Override public void initZygote(StartupParam startupParam){mRes=XModuleResources.createInstance(startupParam.modulePath,null);}
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals(getString("targ"))) return; cl = lpparam.classLoader; bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
		Class<?> homeFrag = findClass("com.wrongchao.v2vpn.ui.home.HomeFragment", cl);
		Class<?> blackListFrag = findClass("com.wrongchao.v2vpn.ui.appblacklist.AppBlackListFragment", cl);

		hookMethod(matchMethod(m -> m.addUsingString("consentInformation").returnType(boolean.class)), replace(p -> false)); //去广告
		hookMethod(matchMethod(m -> m.addUsingField(FieldMatcher.create().name("remainTrafficBytes_")).returnType(long.class)), replace(p -> -1L)); //无限流量

		findAndHookMethod("com.wrongchao.v2vpn.MainActivity", cl, "onCreate", Bundle.class, after(p -> {
			mainActivity = (Activity) p.thisObject;
			res = mainActivity.getResources();
			View drawer = findView("drawer_layout");
			callMethod(drawer, "setDrawerLockMode", 1);
		}));

		hookMethod(matchMethod(m -> m.declaredClass(homeFrag).addUsingString("MM-dd", StringMatchType.Equals)), after(p -> {
			setText("remain_traffic", "∞");
			setText("remain_summary", getString("remain_summary"));
			findView("ads_text").setVisibility(View.GONE);
			findView("free_tag").post(() -> setText("free_tag", getString("free_tag")));
		})); //更改主页文本

		Class<?> service = findClass("com.wrongchao.v2vpn.service.SeTunnelVpnService", cl);
		Class<?> snackbar = matchClass(c -> c.addUsingString("suitable parent"));
		Field connectStatus = matchField(f -> f.declaredClass(service).type(boolean.class));
		Method makeSnackbar = matchMethod(m -> m.declaredClass(snackbar).returnType(snackbar));
		Method showSnackbar = matchMethod(m -> m.declaredClass(snackbar).paramCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL));
		findAndHookMethod(service, "onCreate", after(p -> vpnService = (Service) p.thisObject));
		bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("onCheckedChanged"))).forEach(m -> { try { hookMethod(m.getMethodInstance(cl), before(p -> {
			CompoundButton button = (CompoundButton) p.args[0];
			if (getBooleanField(vpnService, connectStatus.getName())) {
				button.setChecked(!(boolean) p.args[1]);
				callMethod(callStaticMethod(snackbar, makeSnackbar.getName(), button, getString("snackbar_disconnect_before_turn_switch")), showSnackbar.getName());
				p.setResult(null);
			}
		}));} catch (NoSuchMethodException e) { throw new RuntimeException(e); }}); //白名单变更需重连后才生效，所以限制在断开状态下设置，避免用户误认为此设置无效

		Class<?> appItem = matchClass(c -> c.addUsingString("AppItem"));
		FieldMatcher field = FieldMatcher.create().declaredClass(appItem).type(boolean.class).modifiers(Modifier.PUBLIC, MatchType.Equals);
		Class<?> appListAdapter = matchClass(c -> c.addMethod(MethodMatcher.create().addUsingField(field)).addMethod(MethodMatcher.create().paramTypes(List.class)));
		Field shownList = matchField(f -> f.declaredClass(appListAdapter).addReadMethod(MethodMatcher.create().declaredClass(appListAdapter).paramCount(0).returnType(int.class)).addWriteMethod(MethodMatcher.create().declaredClass(appListAdapter).paramTypes(List.class)));
		Field isAppItemEnabled = bridge.findField(FindField.create().matcher(field)).single().getFieldInstance(cl);
		hookMethod(matchMethod(m -> m.declaredClass(appListAdapter).paramTypes(List.class)), after(p -> {
			Object adapter = p.thisObject;
			List orig = (List) getObjectField(adapter, shownList.getName());
			appCount = orig.size(); if (appCount <= 1) return;
			int enabled = 0, disabled;
			for (int i = 0; i < appCount; i++) if (getBooleanField(orig.get(i), isAppItemEnabled.getName())) enabled++;
			disabled = appCount - enabled; boolean shouldPinEnabledToTop = enabled < disabled;
			ArrayList list = new ArrayList();
			for (int i = 0; i < appCount; i++) if (getBooleanField(orig.get(i), isAppItemEnabled.getName()) == shouldPinEnabledToTop) list.add(orig.get(i));
			for (int i = 0; i < appCount; i++) if (getBooleanField(orig.get(i), isAppItemEnabled.getName()) != shouldPinEnabledToTop) list.add(orig.get(i));
			setObjectField(adapter, shownList.getName(), list);
		})); //若只有少数应用启用代理，则将已启用的项置顶，方便后续再次调整

		Class<?> homeMenuClickListener = matchClass(c -> c.addMethod(MethodMatcher.create().name("onMenuItemClick")).addField(FieldMatcher.create().type(homeFrag)));
		Class<?> baseFragment = matchClass(c -> c.addMethod(MethodMatcher.create().name("onCreateContextMenu")));
		Class<?> navController = matchClass(c -> c.addUsingString("destination found"));
		Class<?> navOptions = matchClass(c -> c.addUsingString("restoreState"));
		Field homeFragment = matchField(f -> f.declaredClass(homeMenuClickListener).type(homeFrag));
		Method getNavController = matchMethod(m -> m.paramTypes(baseFragment).returnType(navController));
		Method navigate = matchMethod(m -> m.declaredClass(navController).paramTypes(int.class, navOptions));
		findAndHookMethod(homeMenuClickListener, "onMenuItemClick", MenuItem.class, replace(p -> {
			Object fragment = getObjectField(p.thisObject, homeFragment.getName());
			Object controller = callStaticMethod(getNavController.getDeclaringClass(), getNavController.getName(), fragment);
			callMethod(controller, navigate.getName(), getIdentifier("nav_app_blacklist"), null);
			return true;
		})); //修改按钮入口

		findAndHookMethod("androidx.appcompat.widget.Toolbar", cl, "setNavigationIcon", Drawable.class, replace(p -> null));
		hookMethod(matchMethod(m -> m.declaredClass(homeFrag).paramTypes(Menu.class, MenuInflater.class)), after(p -> {
			Menu menu = (Menu) p.args[0];
			MenuItem item = menu.findItem(getIdentifier("menu_store"));
			item.setIcon(getIdentifier("drawable", "ic_menu_manage")).getIcon().mutate().setTint(Color.WHITE);
			item.setTooltipText(getString("menu_tooltip"));
			menu.findItem(getIdentifier("menu_share")).setVisible(false);
		})); //精简顶部按钮

		hookMethod(makeSnackbar, before(p -> { if (p.args[1].toString().equals(getText("snackbar_tarffic_overflow"))) p.args[1] = getString("snackbar_connect_failure"); }));
		findAndHookMethod("androidx.appcompat.widget.Toolbar", cl, "setTitle", CharSequence.class, before(p -> { if (p.args[0].toString().equals(getText("menu_app_list"))) p.args[0] = getString("menu_app_list"); }));
		hookMethod(matchMethod(m -> m.declaredClass(baseFragment).paramCount(0).returnType(View.class)), after(p -> {
			if (appCount > 0) {
				setText("headet_text", getString("header_switch"));
				findView("switch_view").setVisibility(View.VISIBLE);
			} else {
				setText("headet_text", getString("header_loading"));
				findView("switch_view").setVisibility(View.INVISIBLE);
			}
		}));
		hookMethod(matchMethod(m -> m.declaredClass(ClassMatcher.create().addFieldForType(blackListFrag)).not(MethodMatcher.create().name("<init>")).paramCount(1)), after(p -> {
			if (appCount > 0) {
				setText("headet_text", getString("header_switch"));
				findView("switch_view").setVisibility(View.VISIBLE);
			} else {
				setText("headet_text", getString("header_failed"));
				findView("switch_view").setVisibility(View.INVISIBLE);
			}
		})); //完善应用列表状态显示

		bridge.close();
	}
}