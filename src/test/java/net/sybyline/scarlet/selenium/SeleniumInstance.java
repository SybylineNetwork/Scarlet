package net.sybyline.scarlet.selenium;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;

public abstract class SeleniumInstance<S extends SeleniumInstance<S>> implements AutoCloseable
{

    public static <S extends SeleniumInstance<S>, C extends MutableCapabilities, D extends RemoteWebDriver> S create(Supplier<C> options, Function<C, D> driver, BiFunction<C, Function<C, D>, S> constructor)
    {
        return constructor.apply(options.get(), driver);
    }

    public static <S extends SeleniumInstance<S>> S chrome(BiFunction<ChromeOptions, Function<ChromeOptions, ChromeDriver>, S> constructor)
    {
        return create(ChromeOptions::new, ChromeDriver::new, constructor);
    }
    public static <S extends SeleniumInstance<S>> S edge(BiFunction<EdgeOptions, Function<EdgeOptions, EdgeDriver>, S> constructor)
    {
        return create(EdgeOptions::new, EdgeDriver::new, constructor);
    }
    public static <S extends SeleniumInstance<S>> S firefox(BiFunction<FirefoxOptions, Function<FirefoxOptions, FirefoxDriver>, S> constructor)
    {
        return create(FirefoxOptions::new, FirefoxDriver::new, constructor);
    }

    protected <C extends MutableCapabilities, D extends RemoteWebDriver> SeleniumInstance(C options, Function<C, D> driver)
    {
        options.setCapability(CapabilityType.SET_WINDOW_RECT, true);
        this.driver = driver.apply(options);
    }

    protected final RemoteWebDriver driver;

    public final RemoteWebDriver driver()
    {
        return this.driver;
    }

    public final Window window()
    {
        return new Window();
    }
    public final class Window
    {
        Window()
        {
        }
        public void minimized()
        {
            SeleniumInstance.this.driver.manage().window().minimize();
        }
        public void maximized()
        {
            SeleniumInstance.this.driver.manage().window().maximize();
        }
        public void fullscreen()
        {
            SeleniumInstance.this.driver.manage().window().fullscreen();
        }
        public org.openqa.selenium.Point position()
        {
            return SeleniumInstance.this.driver.manage().window().getPosition();
        }
        public java.awt.Point positionAWT()
        {
            org.openqa.selenium.Point position = this.position();
            return new java.awt.Point(position.x, position.y);
        }
        public void position(org.openqa.selenium.Point position)
        {
            SeleniumInstance.this.driver.manage().window().setPosition(position);
        }
        public void position(int x, int y)
        {
            this.position(new org.openqa.selenium.Point(x, y));
        }
        public void position(java.awt.Point position)
        {
            this.position(position.x, position.y);
        }
        public org.openqa.selenium.Dimension size()
        {
            return SeleniumInstance.this.driver.manage().window().getSize();
        }
        public java.awt.Dimension sizeAWT()
        {
            org.openqa.selenium.Dimension size = this.size();
            return new java.awt.Dimension(size.width, size.height);
        }
        public void size(org.openqa.selenium.Dimension size)
        {
            SeleniumInstance.this.driver.manage().window().setSize(size);
        }
        public void size(int width, int height)
        {
            this.size(new org.openqa.selenium.Dimension(width, height));
        }
        public void size(java.awt.Dimension size)
        {
            this.size(size.width, size.height);
        }
        public org.openqa.selenium.Rectangle bounds()
        {
            org.openqa.selenium.Point position = this.position();
            org.openqa.selenium.Dimension size = this.size();
            return new org.openqa.selenium.Rectangle(position, size);
        }
        public java.awt.Rectangle boundsAWT()
        {
            org.openqa.selenium.Point position = this.position();
            org.openqa.selenium.Dimension size = this.size();
            return new java.awt.Rectangle(position.x, position.y, size.width, size.height);
        }
        public void bounds(org.openqa.selenium.Rectangle bounds)
        {
            this.position(bounds.getPoint());
            this.size(bounds.getDimension());
        }
        public void bounds(int x, int y, int width, int height)
        {
            this.position(x, y);
            this.size(width, height);
        }
        public void bounds(java.awt.Rectangle bounds)
        {
            this.position(bounds.x, bounds.y);
            this.size(bounds.width, bounds.height);
        }
    }

    public final Cookies cookies()
    {
        return new Cookies();
    }
    public final class Cookies
    {
        Cookies()
        {
        }
        public void add(Cookie cookie)
        {
            SeleniumInstance.this.driver.manage().addCookie(cookie);
        }
        public Cookie get(String name)
        {
            return SeleniumInstance.this.driver.manage().getCookieNamed(name);
        }
        public Set<Cookie> get()
        {
            return SeleniumInstance.this.driver.manage().getCookies();
        }
        public void delete(String name)
        {
            SeleniumInstance.this.driver.manage().deleteCookieNamed(name);
        }
        public void delete(Cookie cookie)
        {
            SeleniumInstance.this.driver.manage().deleteCookie(cookie);
        }
        public void deleteAll()
        {
            SeleniumInstance.this.driver.manage().deleteAllCookies();
        }
    }

    protected final S self()
    {
        @SuppressWarnings("unchecked")
        S self = (S)this;
        return self;
    }

    protected CompletableFuture<Void> setElementByIdValue(String id, Object value)
    {
        this.driver.executeScript("document.getElementById('"+id+"').value=arguments[0];", value);
        return CompletableFuture.completedFuture(null);
    }
    protected WebElement getElementById(String id)
    {
        return this.driver.findElement(By.id("request-attachments"));
    }

    @Override
    public void close() throws Exception
    {
        this.driver.quit();
    }

}
