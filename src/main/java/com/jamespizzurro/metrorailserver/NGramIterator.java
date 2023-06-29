package com.jamespizzurro.metrorailserver;

import java.util.Iterator;

public class NGramIterator implements Iterator<String> {

    private String[] words;
    private int pos = 0, n;

    public NGramIterator(int n, String str) {
        this.n = n;
        this.words = str.split(" ");
    }

    public boolean hasNext() {
        return this.pos < this.words.length - this.n + 1;
    }

    public String next() {
        StringBuilder sb = new StringBuilder();
        for (int i = this.pos; i < this.pos + this.n; i++)
            sb.append(i > this.pos ? " " : "").append(this.words[i]);
        this.pos++;
        return sb.toString();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
