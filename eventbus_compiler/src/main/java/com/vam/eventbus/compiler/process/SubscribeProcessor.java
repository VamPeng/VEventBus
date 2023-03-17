package com.vam.eventbus.compiler.process;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.vam.eventbus.annotation.Subscribe;
import com.vam.eventbus.annotation.mode.EventBeans;
import com.vam.eventbus.annotation.mode.SubscriberInfo;
import com.vam.eventbus.annotation.mode.SubscriberMethod;
import com.vam.eventbus.annotation.mode.ThreadMode;
import com.vam.eventbus.compiler.utils.Constants;
import com.vam.eventbus.compiler.utils.EmptyUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class) // javax.annotation.processing.Processor
@SupportedAnnotationTypes({Constants.SUBSCRIBE_ANNOTATION_TYPES})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions({Constants.PACKAGE_NAME, Constants.CLASS_NAME})
public class SubscribeProcessor extends AbstractProcessor {

    private Elements elementUtils;

    // 类信息工具类
    private Types typeUtils;

    // 输出日志
    private Messager messager;

    // 文件生成器
    private Filer filer;

    private String packageName;
    private String className;
    private String subIndex;

    private final Map<TypeElement, List<ExecutableElement>> methodsByClass = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();

        Map<String, String> options = processingEnv.getOptions();
        if (!EmptyUtils.isEmpty(options)) {

            packageName = options.get(Constants.PACKAGE_NAME);
            className = options.get(Constants.CLASS_NAME);

            subIndex = packageName + "." + className;

            // 有坑，不能像android中Log.e的写法
            log("module out: " + subIndex);
        }

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        log("annotations start process");
        if (!EmptyUtils.isEmpty(annotations)) {

            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Subscribe.class);

            if (!EmptyUtils.isEmpty(elements)) {
                try {
                    parseElements(elements);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return true;
        }

        return false;
    }

    private void parseElements(Set<? extends Element> elements) throws IOException {

        for (Element element : elements) {

            if (element.getKind() != ElementKind.METHOD) {
                logE("仅解析@Subscribe注解方法上的元素");
                return;
            }

            ExecutableElement method = (ExecutableElement) element;

            if (checkHasNoErrors(method)) {

                // 方法的类节点
                TypeElement classElement = (TypeElement) method.getEnclosingElement();

                // 缓存
                List<ExecutableElement> methods = methodsByClass.get(classElement);

                if (methods == null) {
                    methods = new ArrayList<>();
                    methodsByClass.put(classElement, methods);
                }
                methods.add(method);

            }

            log("遍历注解方法: " + method.getSimpleName().toString());

        }

        TypeElement subscriberIndexType = elementUtils.getTypeElement(Constants.SUBSCRIBER_INFO_INDEX);

        createFile(subscriberIndexType);

    }

    private void createFile(TypeElement subscriberIndexType) throws IOException {

        CodeBlock.Builder codeBlock = CodeBlock.builder();

        codeBlock.addStatement("$N = new $T<$T,$T>()",
                Constants.FIELD_NAME,
                HashMap.class,
                Class.class,
                SubscriberInfo.class
        );

        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {

            CodeBlock.Builder contentBlock = CodeBlock.builder();
            CodeBlock contentCode = null;
            String format;
            for (int i = 0; i < entry.getValue().size(); i++) {

                Subscribe subscribe = entry.getValue().get(i).getAnnotation(Subscribe.class);

                List<? extends VariableElement> parameters = entry.getValue().get(i).getParameters();

                String methodName = entry.getValue().get(i).getSimpleName().toString();

                TypeElement parameterElement = (TypeElement) typeUtils.asElement(parameters.get(0).asType());

                if (i == entry.getValue().size() - 1) {
                    format = "new $T($T.class, $S, $T.class, $T.$L, $L, $L)";
                } else {
                    format = "new $T($T.class, $S, $T.class, $T.$L, $L, $L),\n";
                }

                contentCode = contentBlock.add(
                        format,
                        SubscriberMethod.class,
                        ClassName.get(entry.getKey()),
                        methodName,
                        ClassName.get(parameterElement),
                        ThreadMode.class,
                        subscribe.threadMode(),
                        subscribe.priority(),
                        subscribe.sticky()
                ).build();

            }

            if (contentCode != null) {
                codeBlock.beginControlFlow("putIndex(new $T($T.class, new $T[]",
                                EventBeans.class,
                                ClassName.get(entry.getKey()),
                                SubscriberMethod.class)
                        .add(contentCode)
                        .endControlFlow("))");
            } else {
                logE("注解处理器双层循环发生错误");
            }

        }

        TypeName fieldType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(Class.class),
                ClassName.get(SubscriberInfo.class)
        );

        ParameterSpec putIndexParameter = ParameterSpec.builder(
                ClassName.get(SubscriberInfo.class),
                Constants.PUT_INDEX_PARAMETER_NAME
        ).build();

        MethodSpec.Builder putIndexBuilder = MethodSpec.methodBuilder(Constants.PUT_INDEX_METHOD_NAME)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(putIndexParameter);

        putIndexBuilder.addStatement("$N.put($N.getSubscriberClass(), $N)",
                Constants.FIELD_NAME,
                Constants.PUT_INDEX_PARAMETER_NAME,
                Constants.PUT_INDEX_PARAMETER_NAME);

        ParameterSpec getSubscriberInfoParameter = ParameterSpec.builder(
                ClassName.get(Class.class),
                Constants.GET_SUBSCRIBER_INFO_PARAMETER_NAME
        ).build();

        MethodSpec.Builder getSubscriberInfoBuilder = MethodSpec.methodBuilder(Constants.GET_SUBSCRIBER_INFO_METHOD_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getSubscriberInfoParameter)
                .returns(SubscriberInfo.class);

        getSubscriberInfoBuilder.addStatement("return $N.get($N)",
                Constants.FIELD_NAME,
                Constants.GET_SUBSCRIBER_INFO_PARAMETER_NAME);

        TypeSpec typeSpec = TypeSpec.classBuilder(className)
                .addSuperinterface(ClassName.get(subscriberIndexType))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addStaticBlock(codeBlock.build())
                .addField(fieldType, Constants.FIELD_NAME, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addMethod(putIndexBuilder.build())
                .addMethod(getSubscriberInfoBuilder.build())
                .build();

        JavaFile.builder(packageName, typeSpec)
                .build()
                .writeTo(filer);


    }

    private boolean checkHasNoErrors(ExecutableElement element) {

        if (element.getModifiers().contains(Modifier.STATIC)) {
            logE("订阅事件方法不能是 static 静态方法", element);
            return false;
        }

        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            logE("订阅事件方法必须是public修饰额方法", element);
            return false;
        }

        List<? extends VariableElement> parameters = element.getParameters();

        if (parameters.size() != 1) {
            logE("订阅事件方法有且仅有一个参数", element);
            return false;
        }

        return true;
    }

    private void log(String content) {
        // 有坑，不能像android中Log.e的写法
        messager.printMessage(Diagnostic.Kind.NOTE, "vam >>> " + content);
    }

    private void logE(String content) {
        // 有坑，不能像android中Log.e的写法
        messager.printMessage(Diagnostic.Kind.ERROR, "vam >>> " + content);
    }

    private void logE(String content, Element element) {
        // 有坑，不能像android中Log.e的写法
        messager.printMessage(Diagnostic.Kind.ERROR, content, element);
    }

}