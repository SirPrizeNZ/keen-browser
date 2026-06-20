#!/usr/bin/env python3
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def apply_replacement(filepath, target, replacement):
    if not os.path.exists(filepath):
        print(f"Error: File not found: {filepath}")
        sys.exit(1)
        
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Normalize line endings to \n
    content = content.replace("\r\n", "\n")
    
    # Perform check if already applied
    # Strip comments and check if target is inside content or already replaced
    if replacement.strip() in content.strip():
        print(f"Patch already applied to {os.path.basename(filepath)}")
        return
        
    # Check if target exists in content
    if target not in content:
        print(f"Error: Target pattern not found in {os.path.basename(filepath)}")
        print("Target pattern tried:")
        print(repr(target))
        sys.exit(1)
        
    new_content = content.replace(target, replacement)
    with open(filepath, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_content)
    print(f"Successfully patched {os.path.basename(filepath)}")

def main():
    if len(sys.argv) > 1:
        apktool_dir = sys.argv[1]
    else:
        apktool_dir = "apktool-smali-32"
    
    APKTOOL_TREE = os.path.join(ROOT, "analysis", apktool_dir)
    print(f"Applying smali patches to tree: {APKTOOL_TREE}")

    # 1. Patch jn3.smali (D-pad key dispatcher hook)
    vm3_path = os.path.join(APKTOOL_TREE, "smali", "jn3.smali")
    vm3_target = (
        ".method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z\n"
        "    .locals 1\n"
        "\n"
        "    .line 1\n"
        "    invoke-virtual {p0}"
    )
    vm3_replacement = (
        ".method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z\n"
        "    .locals 1\n"
        "\n"
        "    invoke-static {p0, p1}, Lcom/brave/tv/TvCursorController;->handle(Landroid/app/Activity;Landroid/view/KeyEvent;)Z\n"
        "\n"
        "    move-result v0\n"
        "\n"
        "    if-eqz v0, :tv_cursor_not_handled\n"
        "\n"
        "    const/4 v0, 0x1\n"
        "\n"
        "    return v0\n"
        "\n"
        "    :tv_cursor_not_handled\n"
        "    .line 1\n"
        "    invoke-virtual {p0}"
    )
    apply_replacement(vm3_path, vm3_target, vm3_replacement)

    # 2. Patch k72.smali (Popup window mouse hook)
    qa2_path = os.path.join(APKTOOL_TREE, "smali_classes2", "k72.smali")
    qa2_target = (
        "    invoke-virtual {v0, v1}, Lk72;->k(I)V"
    )
    qa2_replacement = (
        "    invoke-virtual {v0, v1}, Lk72;->k(I)V\n"
        "\n"
        "    # TvCursor popup attachment hook\n"
        "    iget-object v1, v0, Lk72;->a:Landroid/content/Context;\n"
        "\n"
        "    check-cast v1, Landroid/app/Activity;\n"
        "\n"
        "    iget-object v3, v0, Lk72;->j:Landroid/view/View;\n"
        "\n"
        "    invoke-static {v1, v3, v2}, Lcom/brave/tv/TvCursorController;->attachPopup(Landroid/app/Activity;Landroid/view/View;Landroid/widget/PopupWindow;)V"
    )
    apply_replacement(qa2_path, qa2_target, qa2_replacement)

    # 3. Patch gg6.smali (First-run engine bypass sequencer)
    zf6_path = os.path.join(APKTOOL_TREE, "smali", "gg6.smali")
    zf6_target = (
        ".method public static b(ZZ)Z\n"
        "    .locals 3\n"
        "\n"
        "    .line 1"
    )
    zf6_replacement = (
        ".method public static b(ZZ)Z\n"
        "    .locals 3\n"
        "\n"
        "    # fre_sequencer_bypass\n"
        "    const/4 v0, 0x0\n"
        "\n"
        "    return v0\n"
        "\n"
        "    .line 1"
    )
    apply_replacement(zf6_path, zf6_target, zf6_replacement)

    # 4. Patch TabWebContentsDelegateAndroidImpl.smali (addNewContents redirect blocker hook to TvPopupBlocker)
    delegate_path = os.path.join(
        APKTOOL_TREE, 
        "smali_classes2", 
        "org", 
        "org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali".replace("org/", "", 1)
    )
    delegate_target = (
        ".method public final addNewContents(Lorg/chromium/content_public/browser/WebContents;Lorg/chromium/content_public/browser/WebContents;Lorg/chromium/url/GURL;ILorg/chromium/chrome/browser/util/WindowFeatures;ZLorg/chromium/chrome/browser/util/PictureInPictureWindowOptions;)Z\n"
        "    .locals 0\n"
        "\n"
        "    .line 1"
    )
    delegate_replacement = (
        ".method public final addNewContents(Lorg/chromium/content_public/browser/WebContents;Lorg/chromium/content_public/browser/WebContents;Lorg/chromium/url/GURL;ILorg/chromium/chrome/browser/util/WindowFeatures;ZLorg/chromium/chrome/browser/util/PictureInPictureWindowOptions;)Z\n"
        "    .locals 1\n"
        "\n"
        "    invoke-static {p1, p2, p3, p4, p6}, Lcom/brave/tv/TvPopupBlocker;->shouldAllowNewContents(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;IZ)Z\n"
        "\n"
        "    move-result v0\n"
        "\n"
        "    if-nez v0, :cond_blocked\n"
        "\n"
        "    const/4 v0, 0x0\n"
        "\n"
        "    return v0\n"
        "\n"
        "    :cond_blocked\n"
        "    .line 1"
    )
    apply_replacement(delegate_path, delegate_target, delegate_replacement)

    # 5. Patch k92.smali to hide VPN, Rewards, Leo AI, and Wallet menu items natively
    s82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "k92.smali")
    s82_target = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 9\n"
        "\n"
        "    .line 1\n"
        "    iget v0, p0, Lk92;->q:I\n"
        "\n"
        "    .line 2\n"
        "    .line 3\n"
        "    const/4 v1, 0x1\n"
        "\n"
        "    .line 4\n"
        "    const/4 v2, 0x0\n"
        "\n"
        "    .line 5\n"
        "    packed-switch v0, :pswitch_data_0"
    )
    s82_replacement = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 9\n"
        "\n"
        "    .line 1\n"
        "    iget v0, p0, Lk92;->q:I\n"
        "\n"
        "    # Intercept and return false for Wallet, Leo AI, Rewards, and VPN\n"
        "    const/4 v1, 0x1\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/4 v1, 0x2\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/4 v1, 0x3\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/16 v1, 0x9\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/16 v1, 0xc\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/16 v1, 0xd\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    const/16 v1, 0xe\n"
        "    if-eq v0, v1, :ret_false\n"
        "\n"
        "    goto :normal_flow\n"
        "\n"
        "    :ret_false\n"
        "    const/4 v0, 0x0\n"
        "    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;\n"
        "    move-result-object v0\n"
        "    return-object v0\n"
        "\n"
        "    :normal_flow\n"
        "    const/4 v1, 0x1\n"
        "\n"
        "    .line 4\n"
        "    const/4 v2, 0x0\n"
        "\n"
        "    .line 5\n"
        "    packed-switch v0, :pswitch_data_0"
    )
    apply_replacement(s82_path, s82_target, s82_replacement)

    # 5b. Patch l92.smali to return false for all cases (Rewards, Wallet, News visibility)
    t82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "l92.smali")
    t82_target = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 0\n"
        "\n"
        "    .line 1\n"
        "    iget p0, p0, Ll92;->q:I\n"
        "\n"
        "    .line 2\n"
        "    .line 3\n"
        "    packed-switch p0, :pswitch_data_0"
    )
    t82_replacement = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 1\n"
        "\n"
        "    # Force all cases to return Boolean.FALSE\n"
        "    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;\n"
        "    return-object v0\n"
        "\n"
        "    .line 1\n"
        "    iget p0, p0, Ll92;->q:I\n"
        "\n"
        "    .line 2\n"
        "    .line 3\n"
        "    packed-switch p0, :pswitch_data_0"
    )
    apply_replacement(t82_path, t82_target, t82_replacement)

    # 5c. Patch n92.smali to truncate e() method (removes mobile-only menu items)
    v82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "n92.smali")
    v82_target = (
        "    invoke-virtual {v7, v2}, Lx09;->D(Ljava/lang/Object;)V\n"
        "\n"
        "    .line 2642\n"
        "    .line 2643\n"
        "    .line 2644\n"
        "    :cond_5e"
    )
    v82_replacement = (
        "    invoke-virtual {v7, v2}, Lx09;->D(Ljava/lang/Object;)V\n"
        "\n"
        "    .line 2642\n"
        "    .line 2643\n"
        "    .line 2644\n"
        "    :cond_5e\n"
        "    return-object v7"
    )
    apply_replacement(v82_path, v82_target, v82_replacement)

    # 6. Patch WebContentsObserverProxy.smali to inject JavaScript DOM Scrubber
    observer_path = os.path.join(
        APKTOOL_TREE, 
        "smali_classes2", 
        "org", 
        "org/chromium/content/browser/webcontents/WebContentsObserverProxy.smali".replace("org/", "", 1)
    )
    observer_target = (
        ".method public final didFinishNavigationInPrimaryMainFrame(Lorg/chromium/content_public/browser/NavigationHandle;)V\n"
        "    .locals 2\n"
        "\n"
        "    .line 1\n"
        "    invoke-virtual {p0}, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->j()V"
    )
    observer_replacement = (
        ".method public final didFinishNavigationInPrimaryMainFrame(Lorg/chromium/content_public/browser/NavigationHandle;)V\n"
        "    .locals 2\n"
        "\n"
        "    # Inject DOM Scrubber and update navigation state when navigation finishes\n"
        "    iget-object v0, p0, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->q:Lorg/chromium/content_public/browser/WebContents;\n"
        "    invoke-static {v0}, Lcom/brave/tv/TvPopupBlocker;->onMainFrameNavigationFinished(Ljava/lang/Object;)V\n"
        "\n"
        "    .line 1\n"
        "    invoke-virtual {p0}, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->j()V"
    )
    apply_replacement(observer_path, observer_target, observer_replacement)

    # 7. Patch hs1.smali (shouldOverrideUrlLoading same-tab blocker hook to TvPopupBlocker)
    hs1_path = os.path.join(
        APKTOOL_TREE,
        "smali_classes2",
        "hs1.smali"
    )
    hs1_target = (
        ".method public final w(Lf16;)Lz06;\n"
        "    .locals 37\n"
        "\n"
        "    .line 1"
    )
    hs1_replacement = (
        ".method public final w(Lf16;)Lz06;\n"
        "    .locals 37\n"
        "\n"
        "    # Hook to TvPopupBlocker.shouldOverrideUrlLoading\n"
        "    invoke-static/range {p0 .. p1}, Lcom/brave/tv/TvPopupBlocker;->shouldOverrideUrlLoading(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n"
        "\n"
        "    move-result-object v0\n"
        "\n"
        "    if-eqz v0, :cond_hook_bypass\n"
        "\n"
        "    check-cast v0, Lz06;\n"
        "\n"
        "    return-object v0\n"
        "\n"
        "    :cond_hook_bypass\n"
        "    .line 1"
    )
    # 8. Patch PermissionDialogController.smali (createDialog blocker hook to TvPopupBlocker)
    controller_path = os.path.join(
        APKTOOL_TREE,
        "smali_classes2",
        "org",
        "chromium",
        "components",
        "permissions",
        "PermissionDialogController.smali"
    )
    controller_target = (
        ".method public static createDialog(Lorg/chromium/components/permissions/PermissionDialogDelegate;)V\n"
        "    .locals 2\n"
        "\n"
        "    .line 1\n"
        "    sget-object v0, Ls8c;->a:Lorg/chromium/components/permissions/PermissionDialogController;"
    )
    controller_replacement = (
        ".method public static createDialog(Lorg/chromium/components/permissions/PermissionDialogDelegate;)V\n"
        "    .locals 2\n"
        "\n"
        "    # Hook to TvPopupBlocker.shouldShowPermissionDialog\n"
        "    invoke-static {p0}, Lcom/brave/tv/TvPopupBlocker;->shouldShowPermissionDialog(Ljava/lang/Object;)Z\n"
        "\n"
        "    move-result v0\n"
        "\n"
        "    if-nez v0, :cond_allowed\n"
        "\n"
        "    return-void\n"
        "\n"
        "    :cond_allowed\n"
        "    .line 1\n"
        "    sget-object v0, Ls8c;->a:Lorg/chromium/components/permissions/PermissionDialogController;"
    )
    apply_replacement(controller_path, controller_target, controller_replacement)

if __name__ == "__main__":
    main()
