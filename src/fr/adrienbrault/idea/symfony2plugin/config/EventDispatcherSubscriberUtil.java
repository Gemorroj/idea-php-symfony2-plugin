package fr.adrienbrault.idea.symfony2plugin.config;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.*;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.*;
import fr.adrienbrault.idea.symfony2plugin.Symfony2Icons;
import fr.adrienbrault.idea.symfony2plugin.config.dic.EventDispatcherSubscribedEvent;
import fr.adrienbrault.idea.symfony2plugin.dic.XmlEventParser;
import fr.adrienbrault.idea.symfony2plugin.dic.tags.ServiceTagInterface;
import fr.adrienbrault.idea.symfony2plugin.dic.tags.ServiceTagVisitorInterface;
import fr.adrienbrault.idea.symfony2plugin.stubs.ContainerCollectionResolver;
import fr.adrienbrault.idea.symfony2plugin.util.EventSubscriberUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PsiElementUtils;
import fr.adrienbrault.idea.symfony2plugin.util.service.ServiceXmlParserFactory;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EventDispatcherSubscriberUtil {

    private static final Key<CachedValue<Collection<EventDispatcherSubscribedEvent>>> EVENT_SUBSCRIBERS = new Key<CachedValue<Collection<EventDispatcherSubscribedEvent>>>("SYMFONY_EVENT_SUBSCRIBERS");

    @NotNull
    public static Collection<EventDispatcherSubscribedEvent> getSubscribedEvents(final @NotNull Project project) {

        CachedValue<Collection<EventDispatcherSubscribedEvent>> cache = project.getUserData(EVENT_SUBSCRIBERS);
        if (cache == null) {
            cache = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Collection<EventDispatcherSubscribedEvent>>() {
                @Nullable
                @Override
                public Result<Collection<EventDispatcherSubscribedEvent>> compute() {
                    return Result.create(getSubscribedEventsProxy(project), PsiModificationTracker.MODIFICATION_COUNT);
                }
            }, false);
            project.putUserData(EVENT_SUBSCRIBERS, cache);
        }

        return cache.getValue();
    }

    @NotNull
    private static Collection<EventDispatcherSubscribedEvent> getSubscribedEventsProxy(@NotNull Project project) {

        Collection<EventDispatcherSubscribedEvent> events = new ArrayList<EventDispatcherSubscribedEvent>();

        // http://symfony.com/doc/current/components/event_dispatcher/introduction.html
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Collection<PhpClass> phpClasses = phpIndex.getAllSubclasses("\\Symfony\\Component\\EventDispatcher\\EventSubscriberInterface");

        for(PhpClass phpClass: phpClasses) {

            if(PhpElementsUtil.isTestClass(phpClass)) {
                continue;
            }

            Method method = phpClass.findMethodByName("getSubscribedEvents");
            if(method != null) {
                PhpReturn phpReturn = PsiTreeUtil.findChildOfType(method, PhpReturn.class);
                if(phpReturn != null) {
                    attachSubscriberEventNames(events, phpClass, phpReturn);
                }
            }
        }

       return events;
    }

    private static void attachSubscriberEventNames(@NotNull Collection<EventDispatcherSubscribedEvent> events, @NotNull PhpClass phpClass, @NotNull PhpReturn phpReturn) {

        PhpPsiElement array = phpReturn.getFirstPsiChild();
        if(!(array instanceof ArrayCreationExpression)) {
            return;
        }

        String presentableFQN = phpClass.getPresentableFQN();
        if(presentableFQN == null) {
            return;
        }

        Iterable<ArrayHashElement> arrayHashElements = ((ArrayCreationExpression) array).getHashElements();
        for(ArrayHashElement arrayHashElement: arrayHashElements) {
            PsiElement arrayKey = arrayHashElement.getKey();

            PsiElement value = null;
            // get method name
            // @TODO: support multiple method names, currently we only use method name if type hint, so first item helps for now
            Collection<PsiElement> subscriberMethods = getSubscriberMethods(arrayHashElement);
            if(subscriberMethods.size() > 0) {
                value = subscriberMethods.iterator().next();
            }

            if(arrayKey instanceof StringLiteralExpression) {

                // ['doh' => 'method']
                events.add(new EventDispatcherSubscribedEvent(
                    ((StringLiteralExpression) arrayKey).getContents(),
                    presentableFQN,
                    PhpElementsUtil.getStringValue(value)
                ));

            } else if(arrayKey instanceof PhpReference) {
                String resolvedString = PhpElementsUtil.getStringValue(arrayKey);
                if(resolvedString != null) {

                    // [FOO::BAR => 'method']
                    events.add(new EventDispatcherSubscribedEvent(
                        resolvedString,
                        presentableFQN,
                        PhpElementsUtil.getStringValue(value),
                        ((PhpReference) arrayKey).getSignature())
                    );
                }

            }

        }

    }

    /**
     * Extract method name for subscribe
     *
     * 'pre.foo1' => 'foo'
     * 'pre.foo1' => ['onStoreOrder', 0]
     * 'pre.foo2' => [['onStoreOrder', 0]]
     */
    @NotNull
    private static Collection<PsiElement> getSubscriberMethods(@NotNull ArrayHashElement arrayHashElement) {

        // support string, constants and array values
        PhpPsiElement value = arrayHashElement.getValue();
        if(value == null) {
            Collections.emptySet();
        }

        // 'pre.foo' => [...]
        if(!(value instanceof ArrayCreationExpression)) {
            return new ArrayList<PsiElement>(Collections.singletonList(value));
        }

        Collection<PsiElement> psiElements = new HashSet<PsiElement>();

        // 'pre.foo' => [<caret>]
        PsiElement firstChild = value.getFirstPsiChild();
        if(firstChild != null && firstChild.getNode().getElementType() == PhpElementTypes.ARRAY_VALUE) {
            PhpPsiElement firstPsiChild = ((PhpPsiElement) firstChild).getFirstPsiChild();
            if(firstPsiChild instanceof StringLiteralExpression) {
                // 'pre.foo' => ['method']
                psiElements.add(firstPsiChild);
            } else if(firstPsiChild instanceof ArrayCreationExpression) {

                // 'pre.foo' => [['method', ...], ['method2', ...]]
                for (PsiElement psiElement : PsiElementUtils.getChildrenOfTypeAsList(firstPsiChild, PlatformPatterns.psiElement().withElementType(PhpElementTypes.ARRAY_VALUE))) {
                    if(!(psiElement instanceof PhpPsiElement)) {
                        continue;
                    }

                    PhpPsiElement prioValue = ((PhpPsiElement) psiElement).getFirstPsiChild();
                    if(prioValue instanceof StringLiteralExpression) {
                        psiElements.add(prioValue);
                    }
                }
            }
        }

        return psiElements;
    }

    @NotNull
    public static Collection<EventDispatcherSubscribedEvent> getSubscribedEvent(@NotNull Project project, @NotNull String eventName) {

        List<EventDispatcherSubscribedEvent> events = new ArrayList<EventDispatcherSubscribedEvent>();

        for(EventDispatcherSubscribedEvent event: getSubscribedEvents(project)) {
            if(event.getStringValue().equals(eventName)) {
                events.add(event);
            }
        }

        return events;
    }

    @NotNull
    public static Collection<PsiElement> getEventPsiElements(@NotNull final Project project, final @NotNull String eventName) {

        final Collection<PsiElement> psiElements = new HashSet<PsiElement>();

        // @TODO: remove
        XmlEventParser xmlEventParser = ServiceXmlParserFactory.getInstance(project, XmlEventParser.class);
        for(EventDispatcherSubscribedEvent event : xmlEventParser.getEventSubscribers(eventName)) {
            PhpClass phpClass = PhpElementsUtil.getClass(project, event.getFqnClassName());
            if(phpClass != null) {
                psiElements.add(phpClass);
            }
        }

        for(EventDispatcherSubscribedEvent event: EventDispatcherSubscriberUtil.getSubscribedEvent(project, eventName)) {
            PhpClass phpClass = PhpElementsUtil.getClass(project, event.getFqnClassName());
            if(phpClass != null) {
                psiElements.add(phpClass);
            }
        }

        final ContainerCollectionResolver.ServiceCollector collector = ContainerCollectionResolver.ServiceCollector.create(project);

        EventSubscriberUtil.visitNamedTag(project, "kernel.event_listener", new ServiceTagVisitorInterface() {
            @Override
            public void visit(@NotNull ServiceTagInterface args) {
                String event = args.getAttribute("event");
                if (StringUtils.isNotBlank(event) && eventName.equals(event)) {
                    String serviceId = args.getServiceId();
                    if(StringUtils.isNotBlank(serviceId)) {
                        String resolve = collector.resolve(serviceId);
                        if(resolve != null) {
                            psiElements.addAll(PhpElementsUtil.getClassesInterface(project, resolve));
                        }
                    }
                }
            }
        });

        return psiElements;
    }


    @NotNull
    public static Collection<LookupElement> getEventNameLookupElements(@NotNull Project project) {

        final List<LookupElement> results = new ArrayList<LookupElement>();

        XmlEventParser xmlEventParser = ServiceXmlParserFactory.getInstance(project, XmlEventParser.class);
        for(EventDispatcherSubscribedEvent event : xmlEventParser.getEvents()) {
            results.add(LookupElementBuilder.create(event.getStringValue()).withTypeText(event.getType(), true).withIcon(Symfony2Icons.EVENT));
        }

        for(EventDispatcherSubscribedEvent event: EventDispatcherSubscriberUtil.getSubscribedEvents(project)) {
            results.add(LookupElementBuilder.create(event.getStringValue()).withTypeText(event.getType(), true).withIcon(Symfony2Icons.EVENT));
        }

        EventSubscriberUtil.visitNamedTag(project, "kernel.event_listener", new ServiceTagVisitorInterface() {
            @Override
            public void visit(@NotNull ServiceTagInterface args) {
                String event = args.getAttribute("event");
                if (event != null && StringUtils.isNotBlank(event)) {
                    results.add(LookupElementBuilder.create(event).withTypeText("kernel.event_listener", true).withIcon(Symfony2Icons.EVENT));
                }
            }
        });

        return results;
    }
}

