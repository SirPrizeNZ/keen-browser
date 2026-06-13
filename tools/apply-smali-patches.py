#!/usr/bin/env python3
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APKTOOL_TREE = os.path.join(ROOT, "analysis", "apktool-smali")

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
    # 1. Patch vm3.smali (D-pad key dispatcher hook)
    vm3_path = os.path.join(APKTOOL_TREE, "smali", "vm3.smali")
    vm3_target = (
        ".method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z\n"
        "    .locals 1\n"
        "\n"
        "    .line 1"
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
        "    .line 1"
    )
    apply_replacement(vm3_path, vm3_target, vm3_replacement)

    # 2. Patch qa2.smali (Popup window mouse hook)
    qa2_path = os.path.join(APKTOOL_TREE, "smali_classes2", "qa2.smali")
    qa2_target = (
        "    invoke-virtual {p0}, Lqa2;->q()V"
    )
    qa2_replacement = (
        "    invoke-virtual {p0}, Lqa2;->q()V\n"
        "\n"
        "    iget-object v0, p0, Lqa2;->b:Landroid/content/Context;\n"
        "\n"
        "    check-cast v0, Landroid/app/Activity;\n"
        "\n"
        "    iget-object v2, p0, Lqa2;->d:Landroid/view/View;\n"
        "\n"
        "    invoke-static {v0, v2, v1}, Lcom/brave/tv/TvCursorController;->attachPopup(Landroid/app/Activity;Landroid/view/View;Landroid/widget/PopupWindow;)V"
    )
    apply_replacement(qa2_path, qa2_target, qa2_replacement)

    # 3. Patch zf6.smali (First-run engine bypass sequencer sequencer_bypass)
    zf6_path = os.path.join(APKTOOL_TREE, "smali", "zf6.smali")
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

    # 5. Patch s82.smali to hide VPN, Rewards, Leo AI, and Wallet menu items natively
    s82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "s82.smali")
    s82_target = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 9\n"
        "\n"
        "    .line 1\n"
        "    iget v0, p0, Ls82;->q:I\n"
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
        "    iget v0, p0, Ls82;->q:I\n"
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

    # 5b. Patch t82.smali to return false for all cases (Rewards, Wallet, News visibility)
    t82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "t82.smali")
    t82_target = (
        ".method public final get()Ljava/lang/Object;\n"
        "    .locals 0\n"
        "\n"
        "    .line 1\n"
        "    iget p0, p0, Lt82;->q:I\n"
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
        "    iget p0, p0, Lt82;->q:I\n"
        "\n"
        "    .line 2\n"
        "    .line 3\n"
        "    packed-switch p0, :pswitch_data_0"
    )
    apply_replacement(t82_path, t82_target, t82_replacement)

    # 5c. Patch v82.smali to truncate S() method (removes mobile-only menu items)
    v82_path = os.path.join(APKTOOL_TREE, "smali_classes2", "v82.smali")
    v82_target = (
        "    .line 244\n"
        "    .line 245\n"
        "    .line 246\n"
        "    :cond_3\n"
        "    new-instance v2, Lfc9;"
    )
    v82_replacement = (
        "    .line 244\n"
        "    .line 245\n"
        "    .line 246\n"
        "    :cond_3\n"
        "    return-object v0\n"
        "\n"
        "    new-instance v2, Lfc9;"
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
        "    .locals 3\n"
        "\n"
        "    .line 1\n"
        "    invoke-virtual {p0}, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->k()V"
    )
    observer_replacement = (
        ".method public final didFinishNavigationInPrimaryMainFrame(Lorg/chromium/content_public/browser/NavigationHandle;)V\n"
        "    .locals 3\n"
        "\n"
        "    # Inject DOM Scrubber into WebContents when navigation finishes\n"
        "    iget-object v0, p0, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->q:Lorg/chromium/content_public/browser/WebContents;\n"
        "    invoke-static {v0}, Lcom/brave/tv/TvPopupBlocker;->injectDomScrubber(Ljava/lang/Object;)V\n"
        "\n"
        "    .line 1\n"
        "    invoke-virtual {p0}, Lorg/chromium/content/browser/webcontents/WebContentsObserverProxy;->k()V"
    )
    apply_replacement(observer_path, observer_target, observer_replacement)

if __name__ == "__main__":
    main()
