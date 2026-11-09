package dev.levrn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Keep vanilla editing/clipboard behavior while measuring, clicking and drawing the same font. */
final class LevrnSearchBox extends EditBox {
    private int anchor,visibleStart;
    private String hint="Search modules, settings…";
    LevrnSearchBox(Font font,int x,int y,int width,int height,Component label) {
        super(font,x,y,width,height,label);
        setBordered(false);setTextShadow(false);
    }
    int anchor() {return anchor;}
    @Override public void setHint(Component value) {super.setHint(value);hint=value.getString();}
    @Override public void setHighlightPos(int index) {super.setHighlightPos(index);anchor=Math.clamp(index,0,getValue().length());}
    private int pixels(String text) {return PixelText.pixels(text,PixelText.interfaceSize());}
    private int position(double mouseX) {
        String text=getValue();int start=Math.clamp(visibleStart,0,text.length());
        double pixel=(mouseX-getX()-2)*PixelText.scale();
        for(int i=start;i<text.length();) {
            int next=text.offsetByCodePoints(i,1);
            double midpoint=(pixels(text.substring(start,i))+pixels(text.substring(start,next)))/2.0;
            if(pixel<midpoint) return i;
            i=next;
        }
        return text.length();
    }
    @Override public void onClick(MouseButtonEvent event,boolean doubled) {
        int index=position(event.x());
        if(doubled) {
            String text=getValue();int first=index,last=index;
            while(first>0 && !Character.isWhitespace(text.charAt(first-1))) first--;
            while(last<text.length() && !Character.isWhitespace(text.charAt(last))) last++;
            moveCursorTo(first,false);moveCursorTo(last,true);
        } else moveCursorTo(index,event.hasShiftDown());
    }
    @Override protected void onDrag(MouseButtonEvent event,double dx,double dy) {moveCursorTo(position(event.x()),true);}
    @Override public int getScreenX(int index) {
        int start=Math.clamp(visibleStart,0,getValue().length());
        int end=Math.clamp(index,start,getValue().length());
        return getX()+2+InterfaceText.width(getValue().substring(start,end));
    }
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float delta) {
        if(!isVisible()) return;
        String text=getValue();int cursor=getCursorPosition(),size=PixelText.interfaceSize();
        int limit=Math.max(1,(int)Math.floor((getWidth()-5)*PixelText.scale()));
        visibleStart=Math.clamp(visibleStart,0,cursor);
        while(visibleStart<cursor && pixels(text.substring(visibleStart,cursor))>limit-2)
            visibleStart=text.offsetByCodePoints(visibleStart,1);
        int end=visibleStart;
        while(end<text.length()) {
            int next=text.offsetByCodePoints(end,1);
            if(pixels(text.substring(visibleStart,next))>limit) break;
            end=next;
        }
        int x=getX()+2,y=getY()+2;
        g.enableScissor(getX(),getY(),getX()+getWidth(),getY()+getHeight());
        if(anchor!=cursor) {
            int a=Math.clamp(anchor,visibleStart,end),c=Math.clamp(cursor,visibleStart,end);
            int ax=x+InterfaceText.width(text.substring(visibleStart,a)),cx=x+InterfaceText.width(text.substring(visibleStart,c));
            g.fill(Math.min(ax,cx),y-1,Math.max(ax,cx),y+10,0x80586B91);
        }
        if(text.isEmpty()) InterfaceText.draw(g,InterfaceText.fit(hint,getWidth()-5),x,y,0xFF949EAF);
        else PixelText.draw(g,text.substring(visibleStart,end),x,y,0xFFE5E9F1,size);
        if(isFocused() && (System.nanoTime()/500_000_000L)%2==0) {
            int cx=x+InterfaceText.width(text.substring(visibleStart,cursor));
            g.fill(cx,y-1,cx+1,y+10,0xFFD5DCE8);
        }
        g.disableScissor();
    }
}
