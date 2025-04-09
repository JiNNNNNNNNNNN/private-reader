package com.lv.tool.privatereader.settings;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置验证器
 * 
 * 用于验证设置的有效性
 */
public interface SettingsValidator {
    /**
     * 验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid) {
            this.valid = valid;
            this.errors = new ArrayList<>();
        }
        
        public ValidationResult(boolean valid, @NotNull List<String> errors) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public void addError(@NotNull String error) {
            errors.add(error);
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true);
        }
        
        public static ValidationResult invalid(@NotNull String error) {
            ValidationResult result = new ValidationResult(false);
            result.addError(error);
            return result;
        }
        
        public static ValidationResult invalid(@NotNull List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
    
    /**
     * 验证设置
     * @param settings 设置对象
     * @return 验证结果
     */
    @NotNull
    ValidationResult validate(@NotNull Object settings);
    
    /**
     * 基本验证器
     */
    class BasicValidator implements SettingsValidator {
        @Override
        public @NotNull ValidationResult validate(@NotNull Object settings) {
            ValidationResult result = new ValidationResult(true);
            
            // 检查空值
            if (settings == null) {
                result.addError("设置对象不能为空");
                return ValidationResult.invalid(result.getErrors());
            }
            
            // 检查基本类型
            if (settings instanceof Number) {
                Number number = (Number) settings;
                if (number.doubleValue() < 0) {
                    result.addError("数值不能为负数");
                }
            }
            
            return result;
        }
    }
} 