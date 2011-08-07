#!/usr/local/bin/perl
#
# fix_mtimes.pl - Fix mtime of file based on its name.
#

use utf8;
use strict;
use warnings;

use Image::ExifTool;
use Image::EXIF::DateTime::Parser;
use File::Next;
use File::Basename;
use Time::Local;


my $DRYRUN = 1;

my $EXIFTOOL = Image::ExifTool->new();
my %VIRTUAL_FS;

my @targets;
for (my $i = 0; $i < scalar(@ARGV); ++$i) {
    if ('--run' eq $ARGV[$i]) {
        $DRYRUN = 0;
    } else {
        push @targets, $ARGV[$i];
    }
}
if (scalar(@targets) <= 0) {
    print "USAGE: $0 [--run] {TARGETS...}\n";
    exit;
}

foreach my $target (@targets) {
    if (-d $target) {
        my $files = File::Next::files($target);
        while (defined(my $file = $files->())) {
            &fix_mtimes($file);
        }
    } else {
        &rename_image($target);
    }
}

sub fix_mtimes
{
    my ($filename) = @_;

    my ($name, $dir) = fileparse($filename);
    if ($name !~ m/^IMG_(\d\d\d\d)(\d\d)(\d\d)_(\d\d)(\d\d)(\d\d)(?:_\d+)?\.(?:JPG|JPEG)$/i) {
        print "DEBUG:skip invalid pattern: $filename\n";
        return;
    }
    my ($Y, $M, $D, $h, $m, $s) = ($1, $2, $3, $4, $5, $6);

    my $name_time = timelocal($s, $m, $h, $D, $M - 1, $Y - 1900);
    if (not defined $name_time) {
        print "WARN:invalid name time: $filename\n";
        return;
    }

    my $modified_time = (stat($filename))[9];

    if (abs($modified_time - $name_time) < 10) {
        print "DEBUG:skip nearly mtime: $filename\n";
        return;
    }

    print "INFO:change mtime: $filename ($name_time)\n";
    if ($DRYRUN == 0) {
        utime(undef, $name_time, $filename);
    }
}

