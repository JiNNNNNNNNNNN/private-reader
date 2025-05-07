package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.settings.NotificationReaderSettingsListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NotificationReaderConfigurable implements Configurable {
    private JPanel mainPanel;
    private JSpinner pageSizeSpinner;
    private JCheckBox showPageNumberCheckBox;
    private JCheckBox showChapterTitleCheckBox;
    private JCheckBox showReadingProgressCheckBox;
    private JCheckBox showButtonsCheckBox;
    private JCheckBox autoReadCheckBox;
    private JSpinner readIntervalSpinner;
    private JSpinner updateIntervalSpinner;
    private JCheckBox enabledCheckBox;
    private final NotificationReaderSettings settings;
    private boolean isModified = false;

    public NotificationReaderConfigurable() {
        this.settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "通知栏阅读设置";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 创建所有UI组件
        // 1. 页面设置
        SpinnerNumberModel pageSizeModel = new SpinnerNumberModel(70, 10, 500, 10);
        pageSizeSpinner = new JSpinner(pageSizeModel);

        // 2. 显示选项
        showPageNumberCheckBox = new JCheckBox("显示页码");
        showChapterTitleCheckBox = new JCheckBox("显示章节标题");
        showReadingProgressCheckBox = new JCheckBox("显示阅读进度");
        showButtonsCheckBox = new JCheckBox("显示导航按钮");

        // 3. 自动阅读设置
        autoReadCheckBox = new JCheckBox("启用自动阅读");
        SpinnerNumberModel readIntervalModel = new SpinnerNumberModel(5, 1, 60, 1);
        readIntervalSpinner = new JSpinner(readIntervalModel);

        // 4. 通知更新设置
        SpinnerNumberModel updateIntervalModel = new SpinnerNumberModel(30, 5, 300, 5);
        updateIntervalSpinner = new JSpinner(updateIntervalModel);

        // 5. 启动设置
        enabledCheckBox = new JCheckBox("启动时自动启用通知栏模式");

        // 加载当前设置
        pageSizeSpinner.setValue(settings.getPageSize());
        showPageNumberCheckBox.setSelected(settings.isShowPageNumber());
        showChapterTitleCheckBox.setSelected(settings.isShowChapterTitle());
        showReadingProgressCheckBox.setSelected(settings.isShowReadingProgress());
        showButtonsCheckBox.setSelected(settings.isShowButtons());
        autoReadCheckBox.setSelected(settings.isAutoRead());
        readIntervalSpinner.setValue(settings.getReadIntervalSeconds());
        updateIntervalSpinner.setValue(settings.getUpdateInterval());
        enabledCheckBox.setSelected(settings.isEnabled());

        // 添加即时预览功能
        pageSizeSpinner.addChangeListener(e -> {
            isModified = true;
            if (settings.getPageSize() != (Integer) pageSizeSpinner.getValue()) {
                settings.setPageSize((Integer) pageSizeSpinner.getValue());
                notifySettingsChanged();
            }
        });

        showPageNumberCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isShowPageNumber() != showPageNumberCheckBox.isSelected()) {
                settings.setShowPageNumber(showPageNumberCheckBox.isSelected());
                notifySettingsChanged();
            }
        });

        showChapterTitleCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isShowChapterTitle() != showChapterTitleCheckBox.isSelected()) {
                settings.setShowChapterTitle(showChapterTitleCheckBox.isSelected());
                notifySettingsChanged();
            }
        });

        showReadingProgressCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isShowReadingProgress() != showReadingProgressCheckBox.isSelected()) {
                settings.setShowReadingProgress(showReadingProgressCheckBox.isSelected());
                notifySettingsChanged();
            }
        });

        showButtonsCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isShowButtons() != showButtonsCheckBox.isSelected()) {
                settings.setShowButtons(showButtonsCheckBox.isSelected());
                notifySettingsChanged();
            }
        });

        autoReadCheckBox.addItemListener(e -> {
            isModified = true;
            boolean selected = autoReadCheckBox.isSelected();
            if (settings.isAutoRead() != selected) {
                settings.setAutoRead(selected);
                readIntervalSpinner.setEnabled(selected);
                notifySettingsChanged();
            }
        });

        readIntervalSpinner.addChangeListener(e -> {
            isModified = true;
            if (settings.getReadIntervalSeconds() != (Integer) readIntervalSpinner.getValue()) {
                settings.setReadIntervalSeconds((Integer) readIntervalSpinner.getValue());
                notifySettingsChanged();
            }
        });

        updateIntervalSpinner.addChangeListener(e -> {
            isModified = true;
            if (settings.getUpdateInterval() != (Integer) updateIntervalSpinner.getValue()) {
                settings.setUpdateInterval((Integer) updateIntervalSpinner.getValue());
                notifySettingsChanged();
            }
        });

        enabledCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isEnabled() != enabledCheckBox.isSelected()) {
                settings.setEnabled(enabledCheckBox.isSelected());
                notifySettingsChanged();
            }
        });

        // 初始化组件状态
        readIntervalSpinner.setEnabled(autoReadCheckBox.isSelected());

        // 构建设置面板
        FormBuilder formBuilder = FormBuilder.createFormBuilder();

        // 1. 页面设置部分
        formBuilder.addComponent(new JBLabel("【页面设置】"));
        formBuilder.addLabeledComponent(new JBLabel("每页字数: "), pageSizeSpinner);

        // 2. 显示选项部分
        formBuilder.addComponent(new JBLabel(" "));
        formBuilder.addComponent(new JBLabel("【显示选项】"));
        formBuilder.addComponent(showPageNumberCheckBox);
        formBuilder.addComponent(showChapterTitleCheckBox);
        formBuilder.addComponent(showReadingProgressCheckBox);
        formBuilder.addComponent(showButtonsCheckBox);

        // 3. 自动阅读部分
        formBuilder.addComponent(new JBLabel(" "));
        formBuilder.addComponent(new JBLabel("【自动阅读】"));
        formBuilder.addComponent(autoReadCheckBox);
        formBuilder.addLabeledComponent(new JBLabel("翻页间隔(秒): "), readIntervalSpinner);

        // 4. 通知更新部分
        formBuilder.addComponent(new JBLabel(" "));
        formBuilder.addComponent(new JBLabel("【通知更新】"));
        formBuilder.addLabeledComponent(new JBLabel("刷新间隔(秒): "), updateIntervalSpinner);

        // 5. 启动设置部分
        formBuilder.addComponent(new JBLabel(" "));
        formBuilder.addComponent(new JBLabel("【启动设置】"));
        formBuilder.addComponent(enabledCheckBox);

        // 添加填充组件
        formBuilder.addComponentFillVertically(new JPanel(), 0);

        mainPanel = formBuilder.getPanel();
        return mainPanel;
    }

    private void notifySettingsChanged() {
        ApplicationManager.getApplication()
            .getMessageBus()
            .syncPublisher(NotificationReaderSettingsListener.TOPIC)
            .settingsChanged();
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() {
        // 保存所有设置
        settings.setPageSize((Integer) pageSizeSpinner.getValue());
        settings.setShowPageNumber(showPageNumberCheckBox.isSelected());
        settings.setShowChapterTitle(showChapterTitleCheckBox.isSelected());
        settings.setShowReadingProgress(showReadingProgressCheckBox.isSelected());
        settings.setShowButtons(showButtonsCheckBox.isSelected());
        settings.setAutoRead(autoReadCheckBox.isSelected());
        settings.setReadIntervalSeconds((Integer) readIntervalSpinner.getValue());
        settings.setUpdateInterval((Integer) updateIntervalSpinner.getValue());
        settings.setEnabled(enabledCheckBox.isSelected());

        // 保存设置到持久化存储
        settings.saveSettings();
        isModified = false;
    }

    @Override
    public void reset() {
        // 从设置加载所有值
        pageSizeSpinner.setValue(settings.getPageSize());
        showPageNumberCheckBox.setSelected(settings.isShowPageNumber());
        showChapterTitleCheckBox.setSelected(settings.isShowChapterTitle());
        showReadingProgressCheckBox.setSelected(settings.isShowReadingProgress());
        showButtonsCheckBox.setSelected(settings.isShowButtons());
        autoReadCheckBox.setSelected(settings.isAutoRead());
        readIntervalSpinner.setValue(settings.getReadIntervalSeconds());
        updateIntervalSpinner.setValue(settings.getUpdateInterval());
        enabledCheckBox.setSelected(settings.isEnabled());

        // 更新组件状态
        readIntervalSpinner.setEnabled(autoReadCheckBox.isSelected());

        isModified = false;
    }
}