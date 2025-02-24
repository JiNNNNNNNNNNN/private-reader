package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MainConfigurable implements Configurable {
    private JPanel mainPanel;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Private Reader";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (mainPanel == null) {
            mainPanel = new JPanel(new VerticalFlowLayout());
            JLabel label = new JLabel("<html>" +
                "<h2>Private Reader 设置</h2>" +
                "<p>在这里可以配置 Private Reader 插件的各项设置：</p>" +
                "<ul>" +
                "<li><b>插件设置</b> - 启用/禁用插件功能</li>" +
                "<li><b>字体设置</b> - 自定义阅读界面的字体、大小和颜色</li>" +
                "<li><b>缓存设置</b> - 配置章节内容的缓存策略" +
                "<ul>" +
                "<li>仅影响章节内容的缓存，不影响章节列表</li>" +
                "<li>缓存用于在网络不可用时提供离线阅读</li>" +
                "<li>可以设置缓存大小限制和过期时间</li>" +
                "<li>过期或超出大小限制的缓存会被自动清理</li>" +
                "</ul></li>" +
                "</ul>" +
                "<p><i>注意：章节列表的缓存是独立的，可以通过工具栏的'刷新'按钮手动更新。</i></p>" +
                "</html>");
            mainPanel.add(label);
        }
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        // 父菜单不需要保存设置
    }
} 